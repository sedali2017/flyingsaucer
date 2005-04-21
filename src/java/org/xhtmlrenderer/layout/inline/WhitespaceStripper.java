/*
 * WhitespaceStripper.java
 * Copyright (c) 2004, 2005 Torbj�rn Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package org.xhtmlrenderer.layout.inline;


import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.IdentValue;
import org.xhtmlrenderer.css.newmatch.CascadedStyle;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.layout.Context;
import org.xhtmlrenderer.layout.content.FloatedBlockContent;
import org.xhtmlrenderer.layout.content.StylePop;
import org.xhtmlrenderer.layout.content.StylePush;
import org.xhtmlrenderer.layout.content.TextContent;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;


/**
 * Description of the Class
 *
 * @author Torbj�rn Gannholm
 */
public class WhitespaceStripper {
    /**
     * Description of the Field
     */
    public final static String SPACE = " ";
    /**
     * Description of the Field
     */
    public final static String EOL = "\n";
    // update this to work on linefeeds on multiple platforms;
    /**
     * Description of the Field
     */
    public final static Pattern linefeed_space_collapse = Pattern.compile("\\s+\\n\\s+");//Pattern is thread-safe
    /**
     * Description of the Field
     */
    public final static Pattern linefeed_to_space = Pattern.compile("\\n");
    /**
     * Description of the Field
     */
    public final static Pattern tab_to_space = Pattern.compile("\\t");
    /**
     * Description of the Field
     */
    public final static Pattern space_collapse = Pattern.compile("( )+");


    /**
     * Strips whitespace early in inline content generation. This can be done
     * because "whitespage" does not ally to :first-line and :first-letter. For
     * dynamic pseudo-classes we are allowed to choose which properties apply.
     *
     * @param c
     * @param inlineContent
     * @return a list cleaned of empty content and the thereby
     *         redundant style-changes
     */
    public static List stripInlineContent(Context c, List inlineContent) {
        List stripped = new LinkedList();
        List pendingStylePushes = new LinkedList();
        boolean collapse = false;
        boolean allWhitespace = true;

        for (Iterator i = inlineContent.iterator(); i.hasNext();) {
            Object o = i.next();
            if (o instanceof StylePush) {
                pendingStylePushes.add(o);
                CascadedStyle style;
                StylePush sp = (StylePush) o;
                if (sp.getPseudoElement() != null) {
                    style = c.getCss().getPseudoElementStyle(sp.getElement(), sp.getPseudoElement());
                } else {
                    style = c.getCss().getCascadedStyle(sp.getElement(), false);//already done in ContentUtil
                }
                c.pushStyle(style);
                continue;
            }
            if (o instanceof TextContent) {
                TextContent tc = (TextContent) o;
                CalculatedStyle style = c.getCurrentStyle();
                boolean collapseNext = stripWhitespace(style, collapse, tc);
                if (!tc.isRemovableWhitespace()) {
                    allWhitespace = false;
                    stripped.addAll(pendingStylePushes);
                    pendingStylePushes.clear();
                    stripped.add(tc);
                    collapse = collapseNext;
                }
                continue;
            }
            if (o instanceof StylePop) {
                c.popStyle();
                if (pendingStylePushes.size() != 0) {
                    //redundant style-change
                    pendingStylePushes.remove(pendingStylePushes.size() - 1);
                } else {
                    stripped.add(o);
                }
                continue;
            }
            //Here we have some other object, just add it with preceding styles
            if (!(o instanceof FloatedBlockContent)) {
                allWhitespace = false;
            }
            stripped.addAll(pendingStylePushes);
            pendingStylePushes.clear();
            stripped.add(o);
            collapse = false;//no collapsing of the next one
        }

        //there may be relevant StylePushes pending, e.g. if this is content of AnonymousBlock
        stripped.addAll(pendingStylePushes);

        // Uu.p("final stripped = " + stripped);
        // Uu.p("all whitespace = " + allWhitespace);
        if (allWhitespace) {
            stripWhitespaceContent(stripped);
        }
        return stripped;
    }

    /**
     * Gets the whitespace attribute of the WhitespaceStripper class
     *
     * @param style PARAM
     * @return The whitespace value
     */
    public static IdentValue getWhitespace(CalculatedStyle style) {
        IdentValue whitespace = style.getIdent(CSSName.WHITE_SPACE);
        return whitespace;
    }

    /**
     * this function strips all whitespace from the text according to the CSS
     * 2.1 spec on whitespace handling. It accounts for the different whitespace
     * settings like normal, nowrap, pre, etc
     *
     * @param style
     * @param collapseLeading
     * @param tc              the TextContent to strip. The text in it is
     *                        modified.
     * @return whether the next leading space should collapse or
     *         not.
     */
    static boolean stripWhitespace(CalculatedStyle style, boolean collapseLeading, TextContent tc) {

        IdentValue whitespace = style.getIdent(CSSName.WHITE_SPACE);
        String text = tc.getText();

        // do step 1
        if (whitespace == IdentValue.NORMAL || whitespace == IdentValue.NOWRAP || whitespace == IdentValue.PRE) {
            text = linefeed_space_collapse.matcher(text).replaceAll(EOL);
        }

        // do step 2
        // pull out pre's for breaking
        // OK: any spaces in a pre or pre-wrap are considered to be non-breaking
        // resolve that later, the space-sequence may only be broken at the end!


        // do step 3
        // convert line feeds to spaces
        if (whitespace == IdentValue.NORMAL || whitespace == IdentValue.NOWRAP) {
            text = linefeed_to_space.matcher(text).replaceAll(SPACE);
        }

        // do step 4
        if (whitespace == IdentValue.NORMAL || whitespace == IdentValue.NOWRAP || whitespace == IdentValue.PRE) {
            text = tab_to_space.matcher(text).replaceAll(SPACE);
            text = space_collapse.matcher(text).replaceAll(SPACE);

            // collapse first space against prev inline
            if (text.startsWith(SPACE) &&
                    collapseLeading) {
                text = text.substring(1, text.length());
            }
        }

        boolean collapseNext = (text.endsWith(SPACE) &&
                (whitespace == IdentValue.NORMAL || whitespace == IdentValue.NOWRAP || whitespace == IdentValue.PRE));

        tc.setText(text);
        if (text.trim().equals("")) {
            if (whitespace == IdentValue.NORMAL || whitespace == IdentValue.NOWRAP) {
                tc.setRemovableWhitespace(true);
            } else if (whitespace == IdentValue.PRE) {
                tc.setRemovableWhitespace(false);//actually unnecessary, is set to this by default
            } else if (text.indexOf(EOL) < 0) {//and whitespace.equals("pre-line"), the only one left
                tc.setRemovableWhitespace(true);
            }
        }
        return collapseNext;
    }


    /**
     * Description of the Method
     *
     * @param list PARAM
     */
    private static void stripWhitespaceContent(List list) {
        for (Iterator i = list.iterator(); i.hasNext();) {
            if (i.next() instanceof TextContent) {
                i.remove();
            }
        }

    }
}

