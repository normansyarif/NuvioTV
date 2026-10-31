package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CinematicFocusPositionTest {
    @Test
    fun verticalMovesRestoreEachRowsOwnTile() {
        val sizes = listOf(12, 8, 20)
        val columns = listOf(2, 4, 3)
        var position = CinematicFocusPosition(1, 4)
        position = position.moveVertically(1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(2, 3), position)
        position = position.moveVertically(-1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(1, 4), position)
        position = position.moveVertically(-1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(0, 2), position)
    }

    @Test
    fun destinationWindowCanStartAtTileFourWithoutCopyingSourceColumn() {
        val position = CinematicFocusPosition(0, 4)
            .moveVertically(1, listOf(12, 12), true, listOf(4, 3))
        assertEquals(CinematicFocusPosition(1, 3), position)
    }

    @Test
    fun horizontalMovementDoesNotChangeOtherRowsSelections() {
        val sizes = listOf(20, 10, 30)
        val columns = listOf(12, 3, 7)
        var position = CinematicFocusPosition(0, 12).moveHorizontally(1, sizes)
        assertEquals(CinematicFocusPosition(0, 13), position)
        position = position.moveVertically(1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(1, 3), position)
        position = position.moveVertically(1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(2, 7), position)
    }

    @Test
    fun shortRowsClampOnlyTheirOwnRememberedTile() {
        val sizes = listOf(10, 2, 12)
        val columns = listOf(6, 4, 8)
        var position = CinematicFocusPosition(0, 6).moveVertically(1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(1, 1), position)
        position = position.moveVertically(1, sizes, true, columns)
        assertEquals(CinematicFocusPosition(2, 8), position)
    }

    @Test
    fun repeatedUpReachesHeroThenNavigationAndStops() {
        val sizes = listOf(5, 5)
        var position = CinematicFocusPosition(1, 2)
        for (expectedRow in listOf(0, -1, -2, -2, -2)) {
            position = position.moveVertically(-1, sizes, true, listOf(3, 2))
            assertEquals(expectedRow, position.row)
        }
    }

    @Test
    fun navigationSkipsMissingHeroAndLastRowDoesNotOverflow() {
        val sizes = listOf(5, 5)
        val columns = listOf(1, 3)
        var position = CinematicFocusPosition(0, 1).moveVertically(-1, sizes, false, columns)
        assertEquals(-2, position.row)
        position = position.moveVertically(1, sizes, false, columns)
        assertEquals(CinematicFocusPosition(0, 1), position)
        repeat(4) { position = position.moveVertically(1, sizes, false, columns) }
        assertEquals(CinematicFocusPosition(1, 3), position)
    }

    @Test
    fun unvisitedRowDefaultsToItsFirstTileAndHorizontalEdgesStayInTheirRow() {
        val sizes = listOf(3, 5)
        assertEquals(CinematicFocusPosition(1, 0), CinematicFocusPosition(0, 2).moveVertically(1, sizes, true, emptyList()))
        assertEquals(CinematicFocusPosition(0, 0), CinematicFocusPosition(0, 0).moveHorizontally(-1, sizes))
        assertEquals(CinematicFocusPosition(0, 2), CinematicFocusPosition(0, 2).moveHorizontally(1, sizes))
        assertEquals(CinematicFocusPosition(-1, 0), CinematicFocusPosition(-1, 0).moveHorizontally(1, sizes))
    }
}
