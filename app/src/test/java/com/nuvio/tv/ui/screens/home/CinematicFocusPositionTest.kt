package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CinematicFocusPositionTest {
    @Test
    fun verticalRoundTripKeepsTheSelectedColumn() {
        val sizes = listOf(12, 8, 20, 10)
        var position = CinematicFocusPosition(0, 2)
        for (row in 1..3) {
            position = position.moveVertically(1, sizes, true)
            assertEquals(CinematicFocusPosition(row, 2), position)
        }
        for (row in 2 downTo 0) {
            position = position.moveVertically(-1, sizes, true)
            assertEquals(CinematicFocusPosition(row, 2), position)
        }
    }

    @Test
    fun shortRowsClampWithoutLosingThePreferredColumn() {
        val sizes = listOf(10, 2, 12)
        var position = CinematicFocusPosition(0, 6)
        position = position.moveVertically(1, sizes, true)
        assertEquals(CinematicFocusPosition(1, 1, 6), position)
        position = position.moveVertically(1, sizes, true)
        assertEquals(CinematicFocusPosition(2, 6), position)
        position = position.moveVertically(-1, sizes, true)
        position = position.moveVertically(-1, sizes, true)
        assertEquals(CinematicFocusPosition(0, 6), position)
    }

    @Test
    fun repeatedUpReachesHeroThenNavigationAndStops() {
        val sizes = listOf(5, 5)
        var position = CinematicFocusPosition(1, 2)
        for (expectedRow in listOf(0, -1, -2, -2, -2)) {
            position = position.moveVertically(-1, sizes, true)
            assertEquals(expectedRow, position.row)
            assertEquals(2, position.preferredColumn)
        }
    }

    @Test
    fun navigationSkipsMissingHeroAndLastRowDoesNotOverflow() {
        val sizes = listOf(5, 5)
        var position = CinematicFocusPosition(0, 1).moveVertically(-1, sizes, false)
        assertEquals(-2, position.row)
        position = position.moveVertically(1, sizes, false)
        assertEquals(CinematicFocusPosition(0, 1), position)
        repeat(4) { position = position.moveVertically(1, sizes, false) }
        assertEquals(CinematicFocusPosition(1, 1), position)
    }
    @Test
    fun horizontalSelectionResetsTheColumnForSubsequentRows() {
        val sizes = listOf(20, 10, 30)
        var position = CinematicFocusPosition(0, 12)
        position = position.moveVertically(1, sizes, true)
        assertEquals(CinematicFocusPosition(1, 9, 12), position)
        position = position.moveHorizontally(-1, sizes)
        assertEquals(CinematicFocusPosition(1, 8, 8), position)
        position = position.moveVertically(1, sizes, true)
        assertEquals(CinematicFocusPosition(2, 8, 8), position)
    }

    @Test
    fun horizontalEdgesRemainInTheSameRow() {
        val sizes = listOf(3, 5)
        assertEquals(CinematicFocusPosition(0, 0), CinematicFocusPosition(0, 0).moveHorizontally(-1, sizes))
        assertEquals(CinematicFocusPosition(0, 2), CinematicFocusPosition(0, 2).moveHorizontally(1, sizes))
        assertEquals(CinematicFocusPosition(-1, 0), CinematicFocusPosition(-1, 0).moveHorizontally(1, sizes))
    }

}
