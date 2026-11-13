package com.matuucrea.miltvplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDirectoryTest {
    @Test fun countryCodesAreUniqueAndIsoAlpha2() {
        val codes = AppDirectory.countries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("^[A-Z]{2}$")) })
    }

    @Test fun requestedCountriesArePresent() {
        val codes = AppDirectory.countries.map { it.code }.toSet()
        assertTrue(setOf("AR","BR","US","MX","ES","UY","CL","CO","PE","VE","IT","FR","DE","GB","CA","JP","AU","IN","TH").all(codes::contains))
    }

    @Test fun allProductAreasAreRepresented() {
        assertTrue(AppDirectory.sections.containsAll(listOf(
            "Televisión en vivo","Películas","Series","Deportes","Radios",
            "Música","Teatro y espectáculos","Guía EPG","Adultos +18","Países"
        )))
    }
}
