# What is it?

Simple demo of [Yandex SpeechKit Cloud service](https://tech.yandex.ru/speechkit/cloud/).
You click the "Record" button and say a DnD dice formula, like
"3d6+2". Then the program is to calculate the min, max, average
and roll the dice to generate a random number for the formula.

# How to run

1. Make sure you have JDK 1.8+ installed, and clone this repo if you haven't already.
2. Go to https://developer.tech.yandex.ru/ and generate
an API key for your project (with access to SpeechKit Cloud). It is
free.
3. Edit `src/main/kotlin/demo/SpeechKitService.kt`, put your key there
in the `key` field. There is my key there, but it will stop working at
some moment due to request and time limitations, so get your own key.
4. Execute

```
./gradlew run 
```

if you are on MacOs / Linux, or

```
gradlew.bat run 
```

if you are on Windows.

You can also run `./gradlew jfxJar` to generate an executable jar, or
`./gradlew jfxNative` to generate a native bundle, or `./gradlew tasks`
to see what extra tasks there are.