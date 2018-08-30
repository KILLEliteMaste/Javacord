package org.javacord.core

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.test.appender.ListAppender
import org.javacord.api.entity.server.Server
import org.javacord.api.exception.NotFoundException
import org.javacord.test.MockProxyManager
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.verify.VerificationTimes
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import javax.net.ssl.SSLHandshakeException
import java.util.concurrent.CompletionException

@Subject(DiscordApiImpl)
class DiscordApiImplTest extends Specification {

    @Subject
    def api = new DiscordApiImpl(null, false)

    def 'getAllServers returns all servers'() {
        given:
            Server readyServer = Stub()
            Server nonReadyServer = Stub()
            api.@servers << [0: readyServer]
            api.@nonReadyServers << [1: nonReadyServer]

        expect:
            with(api.allServers) {
                it.size() == 2
                it.containsAll readyServer, nonReadyServer
            }
    }

    @Unroll
    def '#collectionGetter returns unmodifiable collection'() {
        when:
            api."$collectionGetter"(*arguments).clear()

        then:
            thrown UnsupportedOperationException

        where:
            collectionGetter                         | arguments
            'getUnavailableServers'                  | []
            'getCachedUsers'                         | []
            'getCachedUsersByName'                   | [null]
            'getCachedUsersByNameIgnoreCase'         | [null]
            'getCachedUsersByNickname'               | [null, null]
            'getCachedUsersByNicknameIgnoreCase'     | [null, null]
            'getCachedUsersByDisplayName'            | [null, null]
            'getCachedUsersByDisplayNameIgnoreCase'  | [null, null]
            'getServers'                             | []
            'getServersByName'                       | [null]
            'getServersByNameIgnoreCase'             | [null]
            'getCustomEmojis'                        | []
            'getCustomEmojisByName'                  | [null]
            'getCustomEmojisByNameIgnoreCase'        | [null]
            'getRoles'                               | []
            'getRolesByName'                         | [null]
            'getRolesByNameIgnoreCase'               | [null]
            'getChannels'                            | []
            'getGroupChannels'                       | []
            'getPrivateChannels'                     | []
            'getServerChannels'                      | []
            'getChannelCategories'                   | []
            'getServerTextChannels'                  | []
            'getServerVoiceChannels'                 | []
            'getTextChannels'                        | []
            'getVoiceChannels'                       | []
            'getChannelsByName'                      | [null]
            'getChannelsByNameIgnoreCase'            | [null]
            'getTextChannelsByName'                  | [null]
            'getTextChannelsByNameIgnoreCase'        | [null]
            'getVoiceChannelsByName'                 | [null]
            'getVoiceChannelsByNameIgnoreCase'       | [null]
            'getServerChannelsByName'                | [null]
            'getServerChannelsByNameIgnoreCase'      | [null]
            'getChannelCategoriesByName'             | [null]
            'getChannelCategoriesByNameIgnoreCase'   | [null]
            'getServerTextChannelsByName'            | [null]
            'getServerTextChannelsByNameIgnoreCase'  | [null]
            'getServerVoiceChannelsByName'           | [null]
            'getServerVoiceChannelsByNameIgnoreCase' | [null]
            'getGroupChannelsByName'                 | [null]
            'getGroupChannelsByNameIgnoreCase'       | [null]
            'getAllServers'                          | []
    }

    @RestoreSystemProperties
    def 'REST calls with a man-in-the-middle attack fail'() {
        given:
            MockProxyManager.mockProxy.when(
                    HttpRequest.request()
            ) respond HttpResponse.response().withStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            MockProxyManager.setHttpSystemProperties()
            def api = new DiscordApiImpl('fakeBotToken', false)

        when:
            api.applicationInfo.join()

        then:
            CompletionException ce = thrown()
            ce.cause instanceof SSLHandshakeException
    }

    @RestoreSystemProperties
    def 'REST calls with man-in-the-middle attack allowed do not fail with handshake exception'() {
        given:
            MockProxyManager.mockProxy.when(
                    HttpRequest.request()
            ) respond HttpResponse.response().withStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            MockProxyManager.setHttpSystemProperties()
            def api = new DiscordApiImpl('fakeBotToken', true)

        when:
            api.applicationInfo.join()

        then:
            CompletionException ce = thrown()
            ce.cause instanceof NotFoundException
            ce.cause.message == 'Received a 404 response from Discord with body !'

        and:
            MockProxyManager.mockProxy.verify HttpRequest.request(), VerificationTimes.atLeast(1)
    }

    def 'allowing man-in-the-middle attacks logs a warning on api instantiation'() {
        when:
            new DiscordApiImpl('fakeBotToken', true)

        then:
            def expectedWarning = 'All SSL certificates are trusted when connecting to the Discord API and websocket.' +
                    ' This increases the risk of man-in-the-middle attacks!'
            ListAppender.getListAppender('Test Appender').events
                    .findAll { it.level == Level.WARN }
                    .any { it.message.formattedMessage == expectedWarning }
    }

    @RestoreSystemProperties
    def 'REST calls are done via system properties configured HTTP proxy'() {
        given:
            MockProxyManager.mockProxy.when(
                    HttpRequest.request()
            ) respond HttpResponse.response().withStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            MockProxyManager.setHttpSystemProperties()
            def api = new DiscordApiImpl('fakeBotToken', true)

        when:
            api.applicationInfo.join()

        then:
            CompletionException ce = thrown()
            ce.cause instanceof NotFoundException
            ce.cause.message == 'Received a 404 response from Discord with body !'

        and:
            MockProxyManager.mockProxy.verify HttpRequest.request(), VerificationTimes.atLeast(1)
    }

    def 'REST calls are done via system default proxy selector configured proxy'() {
        given:
            MockProxyManager.mockProxy.when(
                    HttpRequest.request()
            ) respond HttpResponse.response().withStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            def defaultProxySelector = ProxySelector.default
            ProxySelector.default = MockProxyManager.proxySelector
            def api = new DiscordApiImpl('fakeBotToken', true)

        when:
            api.applicationInfo.join()

        then:
            CompletionException ce = thrown()
            ce.cause instanceof NotFoundException
            ce.cause.message == 'Received a 404 response from Discord with body !'

        and:
            MockProxyManager.mockProxy.verify HttpRequest.request(), VerificationTimes.atLeast(1)

        cleanup:
            ProxySelector.default = defaultProxySelector
    }

}
