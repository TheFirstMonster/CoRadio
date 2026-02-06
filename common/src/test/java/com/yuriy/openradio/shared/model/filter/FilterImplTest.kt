package app.coradio.shared.model.filter

import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.media.setVariant
import org.junit.Assert
import org.junit.Test

class FilterImplTest {

    private val mFilter = FilterImpl()

    @Test
    fun testFilter1() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "stream.radiojar.com/z4qyckhr9druv")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter2() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "air.pc.cdn.bitgravity.com/air/live/pbaudio174")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter3() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "air.pc.cdn.bitgravity.com/air/live/pbaudio175")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter4() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "playerservices.streamtheworld.com/api/livestream-redirect/HYD_TEL_GSTAAC")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter7() {
        val rs = RadioStation.makeDefaultInstance("123")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter8() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio"
        rs.homePage = "radio.com"
        rs.setVariant(125, "stream.radiojar.com")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter9() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "exclusiu digital (3cat)"
        rs.homePage = ""
        rs.setVariant(96, "https://directes-radio-int.ccma.cat/live-content/radio-oca-hls/master.m3u8")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter10() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio Rainbow"
        rs.homePage = ""
        rs.setVariant(96, "directes-radio-int.ccma.cat")
        Assert.assertFalse(mFilter.filter(rs))
    }
}
