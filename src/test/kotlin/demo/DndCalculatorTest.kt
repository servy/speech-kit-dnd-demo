package demo

import org.junit.Assert
import org.junit.Test

class DndCalculatorTest {
    @Test
    fun simpleTest() {
        val dndCalculator = DndCalculator()
        val result = dndCalculator.calculate("3d8+1")
        Assert.assertEquals(result.min, 4)
        Assert.assertEquals(result.max, 25)
        Assert.assertEquals(result.average, 14.5, 0.000001)
    }
}