package net.palacesoft.gmusic

import org.junit.Test


class SpotifierServiceIT {

    @Test
    public void should_find_song() {
        assert SpotifierService.getSongId("Tqi2ky3hirnuhaynztacz7d3mai", "US").length() == 22
        assert SpotifierService.getSongId("T2cle2yplsd7wnqqytmiezl3xji", "US").length() == 22
        assert SpotifierService.getSongId("Tr5haasr7u52ndyyyf7cwpqmbzi", "US").length() == 22
    }


    @Test(expected = IllegalStateException)
    public void should_fail_to_find_song() {
        assert SpotifierService.getSongId("xe2", "US").length() == 22
    }
}
