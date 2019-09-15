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
    val select = Input(UInt(2.W)) // レジスタ・セレクタ
    val load   = Input(Vec(4, Bool())) // 真の位置のレジスタに値をロードする

    val out    = Output(UInt(4.W)) // テスト用出力値
  })

  // 汎用レジスタ
  val regA = RegInit(1.U(4.W)) // Aレジスタ
  val regB = RegInit(2.U(4.W)) // Bレジスタ
  val regC = RegInit(4.U(4.W)) // (Cレジスタ。最終的には別のものになる)
  val programCounter = RegInit(0.U(4.W)) // プログラム・カウンター

  val carryFlag = RegInit(false.B)

  val selectedVal = MuxLookup(io.select, 0.U(4.W), Seq(
    (0.U(4.W) -> regA),
    (1.U(4.W) -> regB),
    (2.U(4.W) -> regC) // 3.Uの分はデフォルト値で代用
  ))
  val addedVal = selectedVal +& io.iData(3, 0)
  carryFlag := addedVal(4)
  when (io.load(0)) {
    regA := addedVal(3, 0)
  }
  when (io.load(1)) {
    regB := addedVal(3, 0)
  }
  when (io.load(2)) {
    regC := addedVal(3, 0)
  }
  when (io.load(3)) {
    programCounter := addedVal
  } .otherwise {
    programCounter := programCounter + 1.U
  }

  io.iAddr := programCounter
  // 即値を出しておく
  io.out := io.iData(3, 0)
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
    0x00, 0x01, 0x02, 0x03,
    0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B,
    0x0C, 0x0D, 0x0E, 0x0F
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
    val select        = Input(UInt(2.W)) // レジスタ・セレクタ
    val load          = Input(Vec(4, Bool())) // 真の位置のレジスタに値をロードする

    val out           = Output(UInt(4.W)) // LEDへの出力
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
    core.io.select := io.select
    core.io.load := io.load
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
