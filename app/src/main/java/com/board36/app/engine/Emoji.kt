package com.board36.app.engine

import android.graphics.Paint

/**
 * Catalogue d'emoji embarqué dans l'application — aucun réseau, aucune ressource
 * externe : ce sont des chaînes de caractères, rendues par la police du système.
 *
 * Tous les téléphones n'ont pas la même police emoji : un caractère absent s'afficherait
 * en « tofu » (le rectangle vide). [availableIn] écarte donc, à l'ouverture du panneau,
 * ce que la police ne sait pas dessiner.
 */
object Emoji {

    class Category(val label: String, val icon: String, val items: List<String>)

    /** Identifiant de la catégorie des récents, qui n'a pas de contenu figé. */
    const val RECENTS = "récents"

    private fun split(s: String): List<String> = s.trim().split(" ").filter { it.isNotEmpty() }

    val SMILEYS = split(
        "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 🥲 " +
        "😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡 🤐 🤨 😐 😑 😶 🫥 😏 😒 🙄 " +
        "😬 🤥 🫨 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 " +
        "😎 🤓 🧐 😕 🫤 😟 🙁 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 " +
        "😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 " +
        "😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊 💋 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ " +
        "💔 ❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💯 💢 💥 💫 💦 💨 🕳️ 💬 💭 💤"
    )

    val PEOPLE = split(
        "👋 🤚 🖐️ ✋ 🖖 🫱 🫲 🫳 🫴 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 🖕 " +
        "👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦵 " +
        "🦿 🦶 👣 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁️ 👅 👄 🫦 👶 🧒 👦 👧 🧑 👱 " +
        "👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 🥷 👷 🫅 " +
        "🤴 👸 👳 👲 🧕 🤵 👰 🤰 🫃 🫄 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 " +
        "🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 🕴️ 👯 🧖 🧗 🤺 🏇 ⛷️ 🏂 🏌️ 🏄 🚣 🏊 ⛹️ " +
        "🏋️ 🚴 🚵 🤸 🤼 🤽 🤾 🤹 🧘 🛀 🛌 👭 👫 👬 💏 💑 👪 🗣️ 👤 👥 🫂"
    )

    val NATURE = split(
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🐒 🐔 🐧 🐦 🐤 🐣 " +
        "🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 🐌 🐞 🐜 🪰 🪲 🪳 🦟 🦗 🕷️ " +
        "🕸️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 " +
        "🦓 🦍 🦧 🦣 🐘 🦛 🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 " +
        "🐕 🐩 🦮 🐈 🪶 🐓 🦃 🦤 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 🦦 🦥 🐁 🐀 " +
        "🐿️ 🦔 🐾 🐉 🐲 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍 🪴 🎋 🍃 🍂 🍁 🍄 " +
        "🐚 🪨 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 " +
        "🌓 🌔 🌙 🌎 🌍 🌏 🪐 ⭐ 🌟 ✨ ⚡ ☄️ 🔥 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ " +
        "⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💧 ☔ ☂️ 🌊 🌫️"
    )

    val FOOD = split(
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 " +
        "🥒 🌶️ 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🫘 🥐 🥯 🍞 🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇 " +
        "🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🥫 🍝 🍜 🍲 " +
        "🍛 🍣 🍱 🥟 🦪 🍤 🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 " +
        "🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 🫖 ☕ 🍵 🧃 🥤 🧋 🍶 🍺 🍻 🥂 🍷 " +
        "🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️ 🥣 🥡 🥢 🧂"
    )

    val ACTIVITY = split(
        "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 " +
        "🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 🪂 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ " +
        "🎪 🎭 🩰 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 " +
        "🎰 🧩 🪅 🎈 🎉 🎊 🎃 🎁 🎏 🎐 🧧 🎀 🪄 🔮"
    )

    val TRAVEL = split(
        "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍️ 🛺 " +
        "🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 " +
        "🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ 🪝 ⛽ 🚧 🚦 🚥 🗺️ 🗿 " +
        "🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🛖 🏠 🏡 " +
        "🏘️ 🏚️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍 🛕 🕋 ⛩️ " +
        "🌁 🌃 🏙️ 🌄 🌅 🌆 🌇 🌉 🎑 🗾 🏞️ 🌐 🧭 🕰️ ⌛ ⏳ ⌚ ⏰ ⏱️ ⏲️"
    )

    val OBJECTS = split(
        "💡 🔦 🕯️ 🪔 🧯 🛢️ 💸 💵 💴 💶 💷 🪙 💰 💳 🧾 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 " +
        "⚒️ 🛠️ ⛏️ 🪚 🔩 ⚙️ 🪤 🧱 ⛓️ 🧲 🔫 💣 🧨 🪓 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🪦 ⚱️ " +
        "📿 🧿 💈 ⚗️ 🔭 🔬 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡️ 🧹 🪠 🧺 🧻 🚽 🚰 " +
        "🚿 🛁 🧼 🪥 🪒 🧽 🪣 🧴 🛎️ 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🧸 🪆 🖼️ 🪞 🪟 🛍️ 🛒 " +
        "📦 📫 📮 📪 📬 📭 📯 📥 📤 📜 📃 📄 📑 📊 📈 📉 🗒️ 🗓️ 📆 📅 🗑️ 📇 " +
        "🗃️ 🗳️ 🗄️ 📋 📁 📂 🗂️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗 📎 🖇️ " +
        "📐 📏 🧮 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔏 🔐 🔒 🔓 📱 📲 ☎️ " +
        "📞 📟 📠 🔋 🔌 💻 🖥️ 🖨️ ⌨️ 🖱️ 🖲️ 💽 💾 💿 📀 🎥 🎞️ 📽️ 📺 📷 📸 📹 " +
        "📼 📡 📻 🎙️ 🎚️ 🎛️ 🧳 🌂 👓 🕶️ 🥽 🥼 🦺 👔 👕 👖 🧣 🧤 🧥 🧦 👗 👘 " +
        "🥻 🩱 🩲 🩳 👙 👚 👛 👜 👝 🎒 👞 👟 🥾 🥿 👠 👡 👢 👑 👒 🎩 🎓 🧢 " +
        "🪖 ⛑️ 💄 💍 💼"
    )

    val SYMBOLS = split(
        "☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ " +
        "♒ ♓ 🆔 ⚛️ 🉑 ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 " +
        "🈹 🈲 🅰️ 🅱️ 🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 " +
        "❗ ❕ ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹 ❇️ ✳️ ❎ 💠 " +
        "Ⓜ️ 🌀 🏧 🚾 ♿ 🅿️ 🛗 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 ⚧️ 🚻 🚮 🎦 📶 🈁 " +
        "🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 " +
        "🔢 #️⃣ *️⃣ ⏏️ ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ ⏫ ⏬ ◀️ 🔼 🔽 ➡️ ⬅️ ⬆️ ⬇️ " +
        "↗️ ↘️ ↙️ ↖️ ↕️ ↔️ ↪️ ↩️ ⤴️ ⤵️ 🔀 🔁 🔂 🔄 🔃 🎵 🎶 ➕ ➖ ➗ ✖️ 🟰 " +
        "♾️ 💲 💱 ™️ ©️ ®️ 〰️ ➰ ➿ 🔚 🔙 🔛 🔝 🔜 ✔️ ☑️ 🔘 🔴 🟠 🟡 🟢 🔵 " +
        "🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 🔳 🔲 ▪️ ▫️ ◾ ◽ ◼️ ◻️ 🟥 🟧 🟨 🟩 " +
        "🟦 🟪 ⬛ ⬜ 🟫 🔈 🔇 🔉 🔊 🔔 🔕 📣 📢 👁️‍🗨️ 🗯️ ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄"
    )

    val FLAGS = split(
        "🏳️ 🏴 🏁 🚩 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️ 🇫🇷 🇧🇪 🇨🇭 🇨🇦 🇱🇺 🇲🇨 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇬🇧 🇮🇪 🇳🇱 🇦🇹 🇩🇰 " +
        "🇸🇪 🇳🇴 🇫🇮 🇮🇸 🇵🇱 🇨🇿 🇸🇰 🇭🇺 🇷🇴 🇧🇬 🇬🇷 🇭🇷 🇸🇮 🇷🇸 🇺🇦 🇷🇺 🇹🇷 🇪🇺 🇺🇸 🇲🇽 🇧🇷 🇦🇷 " +
        "🇨🇱 🇨🇴 🇵🇪 🇻🇪 🇯🇵 🇨🇳 🇰🇷 🇮🇳 🇮🇩 🇹🇭 🇻🇳 🇵🇭 🇲🇾 🇸🇬 🇦🇺 🇳🇿 🇿🇦 🇲🇦 🇩🇿 🇹🇳 🇪🇬 🇸🇳 " +
        "🇨🇮 🇨🇲 🇲🇱 🇳🇬 🇰🇪 🇪🇹 🇮🇱 🇸🇦 🇦🇪 🇶🇦 🇱🇧 🇮🇷 🇵🇰 🇧🇩"
    )

    /**
     * Catégories dans l'ordre d'affichage. Les récents viennent en tête, remplis à
     * l'usage : leur contenu est fourni par le panneau, pas par cette liste.
     */
    val CATEGORIES: List<Category> = listOf(
        Category(RECENTS, "🕘", emptyList()),
        Category("sourires", "😀", SMILEYS),
        Category("personnes", "👋", PEOPLE),
        Category("animaux", "🐶", NATURE),
        Category("nourriture", "🍎", FOOD),
        Category("activités", "⚽", ACTIVITY),
        Category("voyages", "✈️", TRAVEL),
        Category("objets", "💡", OBJECTS),
        Category("symboles", "❤️", SYMBOLS),
        Category("drapeaux", "🏁", FLAGS)
    )

    /** Tous les emoji du catalogue, toutes catégories confondues. */
    val ALL: List<String> = CATEGORIES.flatMap { it.items }

    /**
     * Écarte de [items] ce que la police de [paint] ne sait pas dessiner.
     *
     * `hasGlyph` ment sur certains environnements (et sur la JVM des tests, où il n'y a
     * pas de police emoji du tout) : si le filtre supprime presque tout, c'est lui qui a
     * tort, et la liste d'origine est renvoyée telle quelle.
     */
    fun availableIn(paint: Paint, items: List<String>): List<String> {
        if (items.isEmpty()) return items
        val kept = items.filter {
            try {
                paint.hasGlyph(it)
            } catch (_: Throwable) {
                true
            }
        }
        return if (kept.size * 4 < items.size) items else kept
    }
}
