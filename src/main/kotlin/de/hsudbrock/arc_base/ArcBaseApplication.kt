package de.hsudbrock.arc_base

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ArcBaseApplication

fun main(args: Array<String>) {
	runApplication<ArcBaseApplication>(*args)
}
