# And-Camera2SlowMotionJava
Googleのサンプル([Camera2SlowMotion](https://github.com/android/camera-samples/tree/main/Camera2SlowMotion))をJavaで再作成したやつ。

kotlinが分からない自分向け。

中身は、<br/>
・Camera起動 → プレビュー → 撮像 → 別Fragmentで表示<br/>
という、基本機能のみ。
ズームとか、ピント合わせとかの機能はない。

・高速FPSできるのが新しいポイント。

※まだ、下記不具合あり。<br/>
・保存した動画が、Androidスマホのアプリでは再生できない。(PCに吸い出して再生すると再生できた)<br/>
・撮影完了 → 戻るボタン押下で、落ちる。<br/>

不具合あるけど、ひとまず、動いたんで良しとする。<br/>
もう、疲れた...。
