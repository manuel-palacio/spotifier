package net.palacesoft.gmusic

import groovy.util.logging.Log
import org.apache.commons.lang.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Log
class SpotifierService {

    static def String getSongId(String googleId, String country) {

        String songName = URLEncoder.encode(getGoogleSongNameFromId(googleId), "UTF-8")

        log.info("Got song name ${songName}")
        def xml = new XmlSlurper().parse("http://ws.spotify.com/search/1/track?q=${songName}")

        def track = xml.track.find { it.name == songName && it.album.availability.territories.text().contains(country) }

        String uri;
        if (track) {
            uri = track.@href.text()
        } else {
            uri = xml.track[0].@href.text()
        }

        return StringUtils.substringAfterLast(uri, ":")


    }

    static String getGoogleSongNameFromId(String id) {
        log.info("Finding song ${id}")

        Document doc = Jsoup.connect("https://play.google.com/music/m/${id}").get()

        def redirect = doc.select("a").attr("href")

        log.info("Redirecting to ${redirect}")

        doc = Jsoup.connect(redirect).get()

        def songElement = doc.select("[data-track-docid=song-${id}]").first()
        if (songElement) {
            songElement.parent().parent().select("[itemprop=name]").text()
        } else {
            throw new IllegalStateException("Could not find song ${id}")
        }
    }
}
