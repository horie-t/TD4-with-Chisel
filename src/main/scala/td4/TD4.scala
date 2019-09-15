package td4

import chisel3._
import chisel3.util._
import chisel3.core.withClockAndReset

/**
  * TD4 CPUコア
  */
class TD4 extends Module {
  val io = IO(new Bundle() {
    val iAddr  = Output(UInt(4.W)) // 命令アドレス
    val iData  = Input(UInt(8.W))  // 命令データ
    val in     = Input(UInt(4.W))  // 入力ポートへ
    val out    = Output(UInt(4.W)) // 出力ポートへ
  })

  /*
   * レジスタ定義
   */
  // 汎用レジスタ
  val regA = RegInit(0.U(4.W)) // Aレジスタ
  val regB = RegInit(0.U(4.W)) // Bレジスタ

  // 出力ポート用レジスタ
  val regOut = RegInit(0.U(4.W))

  // プログラム・カウンタ
  val programCounter = RegInit(0.U(4.W))

  // キャリーフラグ
  val carryFlag = RegInit(false.B)

  /*
   * 命令デコーダ
   */
  val opData = Cat(io.iData(7, 4), carryFlag)
  val ctrlSig = MuxCase(0.U(6.W), Seq(
    // 本のデコーダの設計の最初の真理値表
    ((opData === BitPat("b0000?")) -> "b000111".U), // ADD A,Im
    ((opData === BitPat("b0001?")) -> "b010111".U), // MOV A,B
    ((opData === BitPat("b0010?")) -> "b100111".U), // IN  A
    ((opData === BitPat("b0011?")) -> "b110111".U), // MOV A,Im
    ((opData === BitPat("b0100?")) -> "b001011".U), // MOV B,A
    ((opData === BitPat("b0101?")) -> "b011011".U), // ADD B,Im
    ((opData === BitPat("b0110?")) -> "b101011".U), // IN  B
    ((opData === BitPat("b0111?")) -> "b111011".U), // MOV B,Im
    ((opData === BitPat("b1001?")) -> "b011101".U), // OUT B
    ((opData === BitPat("b1011?")) -> "b111101".U), // OUT Im
    ((opData === BitPat("b11100")) -> "b111110".U), // JNC(C=0)
    ((opData === BitPat("b11101")) -> "b111111".U), // JNC(C=1)"b??1111"
    ((opData === BitPat("b1111?")) -> "b111110".U)  // JMP
  ))
  val select = ctrlSig(5, 4)
  // 本はloadの値は、負論理なので否定して、順序も逆なのでReverseする
  val load   = Reverse(~ctrlSig(3, 0))

  /*
   * レジスタ読み込み
   */
  val selectedVal = MuxLookup(select, 0.U(4.W), Seq(
    (0.U(4.W) -> regA),
    (1.U(4.W) -> regB),
    (2.U(4.W) -> io.in) // 3.Uの分はデフォルト値で代用
  ))

  /*
   * 演算処理(ALU)
   */
  val addedVal = selectedVal +& io.iData(3, 0)
  carryFlag := addedVal(4)

  /*
   * レジスタ書き戻し
   */
  when (load(0)) {
    regA := addedVal(3, 0)
  }
  when (load(1)) {
    regB := addedVal(3, 0)
  }
  when (load(2)) {
    regOut := addedVal(3, 0)
  }
  when (load(3)) {
    programCounter := addedVal
  } .otherwise {
    programCounter := programCounter + 1.U
  }

  // 出力
  io.iAddr := programCounter
  io.out := regOut
}

/**
  * ROM
  */
class ROM extends Module {
  val io = IO(new Bundle() {
    val addr = Input(UInt(4.W)) // アドレス
    val data = Output(UInt(8.W)) // データ
  })

  // ROMの中身
  val rom = VecInit(List(
    // // Lチカプログラム
    // 0xB3, 0xB6, 0xBC, 0xB8,
    // 0xB8, 0xBC, 0xB6, 0xB3,
    // 0xB1, 0xF0, 0x00, 0x00,
    // 0x00, 0x00, 0x00, 0x00

    // ラーメンタイマー プログラム
    0xB7, 0x01, 0xE1, 0x01,
    0xE3, 0xB6, 0x01, 0xE6,
    0x01, 0xE8, 0xB0, 0xB4,
    0x01, 0xEA, 0xB8, 0xFF
  ).map(_.asUInt(8.W)))

  io.data := rom(io.addr)
}

/**
  * TD4のトップモジュール
  */
class TD4Top extends Module {
  val io = IO(new Bundle {
    val cpuReset      = Input(Bool()) // CPU RESETボタンに接続
    val isManualClock = Input(Bool()) // クロック信号をマニュアル操作するか
    val manualClock   = Input(Bool()) // マニュアル・クロック信号
    val isHz10        = Input(Bool()) // 10Hzのクロックで動作するか？ 偽の場合は1Hzで動作します。
    val in            = Input(UInt(4.W))  // 入力ポート
    val out           = Output(UInt(4.W)) // 出力ポート
  })

  // 1Hz, 10Hzのパルスを生成してクロック信号の代わりにする
  val clockFrequency = 100000000    // 使用するFPGAボードの周波数(Hz)
  val (clockCount, hz10Pulse) = Counter(true.B, clockFrequency / 10 / 2)
  val hz10Clock = RegInit(true.B)
  hz10Clock := Mux(hz10Pulse, ~hz10Clock, hz10Clock)
  val (helz10Count, hz1Pulse) = Counter(hz10Pulse, 10)
  val hz1Clock = RegInit(true.B)
  hz1Clock := Mux(hz1Pulse, ~hz1Clock, hz1Clock)

  // マニュアル・クロック用のボタンのチャタリングを除去
  val manualClock = Debounce(io.manualClock, clockFrequency)
  val td4Clock = Mux(io.isManualClock,
    io.manualClock,
    Mux(io.isHz10, hz10Clock, hz1Clock)).asClock

  // CPU RESETボタンは負論理なので反転する。
  withClockAndReset(td4Clock, ~io.cpuReset) {
    val core = Module(new TD4())
    core.io.in := io.in
    io.out := core.io.out

    val rom = Module(new ROM())
    rom.io.addr := core.io.iAddr
    core.io.iData := rom.io.data
  }
}

/**
  *  プッシュボタン用デバンウンス
  */
class Debounce(hz: Int) extends Module {
  val io = IO(new Bundle{
    val in = Input(Bool())
    val out = Output(Bool())
  })

  val (count, enable) = Counter(true.B, hz / 10) // 0.1秒間隔で値を取り込む

  val reg0 = RegEnable(io.in, false.B, enable)
  val reg1 = RegEnable(reg0,  false.B, enable)

  io.out := reg0 && !reg1 && enable // enableの時だけ変化を見るようにして、1クロックのパルスにする
}

/**
  * プッシュボタン用デバンウンスのコンパニオン・オブジェクト
  */
object Debounce {
  def apply(in: Bool, hz: Int): Bool = {
    val debounce = Module(new Debounce(hz))
    debounce.io.in := in
    debounce.io.out
  }
}

/**
  * TD4のVerilogファイルを生成するためのオブジェクト
  */
object TD4Top extends App {
  chisel3.Driver.execute(args, () => new TD4Top)
}
