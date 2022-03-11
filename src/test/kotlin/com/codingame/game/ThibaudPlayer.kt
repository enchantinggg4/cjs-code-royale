package com.codingame.game

import java.io.InputStream
import java.io.PrintStream

class ThibaudPlayer(stdin: InputStream, stdout: PrintStream, stderr: PrintStream): BasePlayer(stdin, stdout, stderr) {


  private fun makeMove(){
    stdout.println("BUILD 0 BARRACKS-KNIGHT")
    stdout.println("TRAIN 0")
  }

  init {
    while (true) {
      readInputs()
      makeMove()
    }
  }
}
