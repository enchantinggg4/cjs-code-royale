import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

fun main() {
    val xGrid = 40
    val yGrid = 25


    val width = xGrid
    val height = yGrid

    val output = BufferedImage(
        width, height,
        BufferedImage.TYPE_INT_RGB
    )
    for (i in 0 until xGrid) {
        for (j in 0 until yGrid) {
            val value = (Math.random() * 255).toInt()
            output.raster.setPixel(i, j, intArrayOf(value, 0, 0))
        }
    }

    ImageIO.write(
        output, "jpeg",
        File("./debug_img/00.jpeg")
    );
}