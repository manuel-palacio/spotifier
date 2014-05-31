package net.palacesoft.gmusic

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@EnableAutoConfiguration
@RequestMapping("/resources/spotifier")
public class Application {

    private def cache = [ removeEldestEntry: { size() > 1000 } ] as LinkedHashMap

    @RequestMapping(value = "/{id}")
    @ResponseBody
    public ResponseEntity<String> getSongUri(
            @PathVariable String id, @RequestParam(defaultValue = "US") String country) throws IOException {

        if (id == null) {
            return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
        }

        String songUri;
        try {
            def trackUri = cache.get(id)
            if (!trackUri) {
                def songId = SpotifierService.getSongId(id, country.toUpperCase())
                songUri = "http://open.spotify.com/track/${songId}"
                cache.put(id, songUri);
            } else {
                songUri = trackUri
            }

        } catch (IllegalStateException e) {
            return new ResponseEntity<String>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<String>(songUri, HttpStatus.OK);
    }

    @Bean
    public static ServerProperties myServerProperties() {
        ServerProperties p = new ServerProperties();
        String portStr = System.getProperty("app.port");  // CloudBees Environment Variable for local port
        int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;
        p.setPort(port);
        return p;
    }

    static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }
}
