/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.freecol.android;

import java.util.ArrayList;
import java.util.List;


/**
 * A page of the reports or the Colopedia: a title, then sections of
 * entries, each a picture, a name, some lines of text and a figure on
 * the right.  Plain Java, so the desktop check can read the pages too;
 * the phone draws them (InfoPanel).
 */
public final class InfoPage {

    /** One entry: a card in the page. */
    public static final class Entry {
        public final String imageKey, title, text, right;
        /** For the Colopedia: the id of the page this entry opens. */
        public String link = null;

        public Entry(String imageKey, String title, String text, String right) {
            this.imageKey = imageKey;
            this.title = title;
            this.text = (text == null) ? "" : text;
            this.right = (right == null) ? "" : right;
        }
    }

    /** A heading and its entries. */
    public static final class Section {
        public final String heading;
        public final List<Entry> entries = new ArrayList<>();
        /** Entries are small: lay them out in columns. */
        public boolean compact = false;

        Section(String heading) {
            this.heading = heading;
        }

        public Entry add(String imageKey, String title, String text, String right) {
            Entry e = new Entry(imageKey, title, text, right);
            entries.add(e);
            return e;
        }
    }

    public final String title;
    /** A line under the title. */
    public String summary = "";
    /** A large picture for the page (Colopedia), or null. */
    public String imageKey = null;
    /** Long text (Colopedia descriptions), shown after the picture. */
    public String body = "";
    public final List<Section> sections = new ArrayList<>();

    public InfoPage(String title) {
        this.title = title;
    }

    public Section section(String heading) {
        Section s = new Section(heading);
        sections.add(s);
        return s;
    }

    /** How many entries there are in all (for the desktop check). */
    public int size() {
        int n = 0;
        for (Section s : sections) n += s.entries.size();
        return n;
    }
}
