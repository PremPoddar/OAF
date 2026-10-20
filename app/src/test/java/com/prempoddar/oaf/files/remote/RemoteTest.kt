package com.prempoddar.oaf.files.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteTest {
    @Test fun pathsStayTidy() {
        assertThat(RemotePath.normal("")).isEqualTo("/")
        assertThat(RemotePath.normal("Media//Films/./x/../")).isEqualTo("/Media/Films")
        assertThat(RemotePath.normal("\\share\\a")).isEqualTo("/share/a")
        assertThat(RemotePath.join("/a", "b c")).isEqualTo("/a/b c")
        assertThat(RemotePath.parent("/a/b")).isEqualTo("/a")
        assertThat(RemotePath.parent("/a")).isEqualTo("/")
        assertThat(RemotePath.name("/a/b.txt")).isEqualTo("b.txt")
    }

    @Test fun addressesReadLikeLinks() {
        assertThat(Server("1", Server.Type.SMB, "NAS", "nas.local", path = "/Media").address).isEqualTo("smb://nas.local/Media")
        assertThat(Server("2", Server.Type.FTP, "Box", "10.0.0.2", port = 2121, secure = true).address).isEqualTo("ftps://10.0.0.2:2121")
        assertThat(Server("3", Server.Type.WEBDAV, "Cloud", "dav.example.org", secure = true, path = "/remote.php/dav").address).isEqualTo("davs://dav.example.org/remote.php/dav")
    }

    @Test fun webDavListingIsRead() {
        val xml = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
 <d:response><d:href>/dav/Photos/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
 <d:response><d:href>/dav/Photos/Summer%20Trip/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>Tue, 01 Oct 2024 10:00:00 GMT</d:getlastmodified></d:prop></d:propstat></d:response>
 <d:response><d:href>https://host.example/dav/Photos/a%2Bb.jpg</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>2048</d:getcontentlength></d:prop></d:propstat></d:response>
</d:multistatus>"""
        val list = WebDav.parse(xml, "/dav/Photos")
        assertThat(list.map { it.name }).containsExactly("Summer Trip", "a+b.jpg").inOrder()
        assertThat(list[0].dir).isTrue()
        assertThat(list[0].modified).isEqualTo(1727776800000L)
        assertThat(list[1].size).isEqualTo(2048)
        assertThat(list[1].path).isEqualTo("/dav/Photos/a+b.jpg")
    }
}
