package com.matuucrea.miltvplus.data

import java.util.Locale

data class CountryOption(val code: String, val name: String, val flag: String)

object AppDirectory {
    val countries: List<CountryOption> by lazy {
        Locale.getISOCountries().mapNotNull { code ->
            val name = Locale("es", code).getDisplayCountry(Locale("es"))
            if (name.isBlank()) null else CountryOption(code, name, flagFor(code))
        }.sortedBy { it.name }
    }

    val sections = listOf(
        "Televisión en vivo", "Películas", "Series", "Deportes", "Radios",
        "Música", "Teatro y espectáculos", "Guía EPG", "Adultos +18", "Países"
    )

    private fun flagFor(code: String): String =
        code.uppercase(Locale.ROOT).map { letter ->
            String(Character.toChars(0x1F1E6 + (letter.code - 'A'.code)))
        }.joinToString("")
}
