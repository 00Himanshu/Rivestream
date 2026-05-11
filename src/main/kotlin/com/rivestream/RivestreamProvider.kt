package com.rivestream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class RivestreamProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(RivestreamExtension())
    }
}
