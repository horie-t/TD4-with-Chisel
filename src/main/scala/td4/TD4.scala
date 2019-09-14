package td4

import chisel3._

/**
  * TD4のトップモジュール
  */
class TD4Top extends Module {
  // ひとまずI/Oは空で定義する
  val io = IO(new Bundle {
  })
}

/**
  * TD4のVerilogファイルを生成するためのオブジェクト
  */
object TD4Top extends App {
  chisel3.Driver.execute(args, () => new TD4Top)
}
