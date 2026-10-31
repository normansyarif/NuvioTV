package com.nuvio.tv.ui.screens.home

/** Rows -1 and -2 represent the hero and navigation bar respectively. */
internal data class CinematicFocusPosition(
    val row: Int,
    val column: Int
) {
    fun moveHorizontally(direction: Int, rowSizes: List<Int>): CinematicFocusPosition {
        require(direction == -1 || direction == 1)
        val count = rowSizes.getOrNull(row) ?: return this
        if (count == 0) return this
        val next = (column + direction).coerceIn(0, count - 1)
        return copy(column = next)
    }

    fun moveVertically(
        direction: Int,
        rowSizes: List<Int>,
        hasHero: Boolean,
        rowColumns: List<Int>
    ): CinematicFocusPosition {
        require(direction == -1 || direction == 1)
        val firstContentRow = if (hasHero) -1 else 0
        if (!hasHero && row == -2 && direction == 1 && rowSizes.isNotEmpty()) {
            return copy(row = 0, column = rowColumns.getOrElse(0) { 0 }.coerceIn(0, (rowSizes.first() - 1).coerceAtLeast(0)))
        }
        val targetRow = (row + direction).coerceIn(-2, (rowSizes.size - 1).coerceAtLeast(firstContentRow))
        if (targetRow < firstContentRow) return copy(row = -2)
        if (targetRow < 0) return copy(row = targetRow)
        return copy(row = targetRow, column = rowColumns.getOrElse(targetRow) { 0 }
            .coerceIn(0, (rowSizes[targetRow] - 1).coerceAtLeast(0)))
    }
}
