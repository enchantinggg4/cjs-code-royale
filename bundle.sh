echo "" > ./out.kt
tail -n +2 src/main/kotlin/com/codingame/game/Vector2.kt >> out.kt
tail -n +2 src/main/kotlin/com/codingame/game/Characters.kt >> out.kt
tail -n +2 src/main/kotlin/com/codingame/game/Constants.kt >> out.kt
tail -n +2 src/main/kotlin/com/codingame/game/Structures.kt >> out.kt

tail -n +2 src/test/kotlin/com/codingame/game/LennyPlayer.kt >> out.kt