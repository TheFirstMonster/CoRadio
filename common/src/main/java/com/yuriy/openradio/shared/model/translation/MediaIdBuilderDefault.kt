package app.coradio.shared.model.translation

class MediaIdBuilderDefault: MediaIdBuilder {

    override fun build(value: String): String {
        return value
    }
}
