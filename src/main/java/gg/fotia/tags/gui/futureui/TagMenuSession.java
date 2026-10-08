package gg.fotia.tags.gui.futureui;

import java.util.UUID;

final class TagMenuSession {
    final UUID token = UUID.randomUUID();
    String view, target = "", filter = "all", menu, inputPart = "";
    int page, returnPage;
    boolean compact, busy;
    TagMenuSession(boolean compact) { this.compact = compact; }
}
