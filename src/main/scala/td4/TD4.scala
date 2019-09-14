package td4

import chisel3._
import chisel3.util._
import chisel3.core.withClockAndReset

/**
  * TD4のトップモジュール
  */
class TD4Top extends Module {
  val io = IO(new Bundle {
    val cpuReset      = Input(Bool()) // CPU RESETボタンに接続
    val isManualClock = Input(Bool()) // クロック信号をマニュアル操作するか
    val manualClock   = Input(Bool()) // マニュアル・クロック信号
    val isHz10        = Input(Bool()) // 10Hzのクロックで動作するか？ 偽の場合は1Hzで動作します。
  })

  // 1Hz, 10Hzのパルスを生成してクロック信号の代わりにする
  val clockFrequency = 1000000    // 使用するFPGAボードの周波数(Hz)に合わせて変更する
  val (clockCount, hz1Pulse) = Counter(true.B, clockFrequency)
  val (helz1Count, hz10Pulse) = Counter(hz1Pulse, 10)

  // マニュアル・クロック用のボタンのチャタリングを除去
  val manualClock = Debounce(io.manualClock, clockFrequency)
  val td4Clock = Mux(io.isManualClock,
    manualClock,
    Mux(io.isHz10, hz10Pulse, hz1Pulse)).asClock

  // CPU RESETボタンは負論理なので反転する。
  withClockAndReset(td4Clock, ~io.cpuReset) {
    // TODO: 実装する
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
