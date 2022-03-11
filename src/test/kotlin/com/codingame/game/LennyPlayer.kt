package com.codingame.game

import java.io.InputStream
import java.io.PrintStream
import java.lang.IllegalArgumentException


import java.util.*
import kotlin.math.*


class LennyPlayer(stdin: InputStream, stdout: PrintStream, stderr: PrintStream) : BasePlayer(stdin, stdout, stderr) {

    init {
        while (true) {

            val inputs = readInputs()
            stderr.println("NIGGER LENNY")
            stdout.println("BUILD 0 BARRACKS-KNIGHT")
            stdout.println("TRAIN 0")
        }
    }
}

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)
    val numSites = input.nextInt()
    for (i in 0 until numSites) {
        val siteId = input.nextInt()
        val x = input.nextInt()
        val y = input.nextInt()
        val radius = input.nextInt()
    }

    // game loop

    LennyPlayer(System.`in`, System.out, System.err)
}