TD4をChiselで実装
================

TD4は4bit CPUです。詳細は、 [CPUの創り方](https://www.amazon.co.jp/dp/4839909865/) を参照してください。

本プロジェクトは、(本の趣旨に反していますが)Chiselという、ハードウェア構築言語(Hardware Construction Language)を使ってTD4を実装するプロジェクトです。

Verilogファイルを生成するには以下のようsbtを実行します。 target ディレクトリ内に「TD4Top.v」というファイルが生成されます。

```
sbt 'runMain td4.TD4Top --target-dir target'
```
