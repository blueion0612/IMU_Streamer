"""Draw the README hero: watch, phone and server, and the two directions between them.

    python docs/figures/make_hero.py

Writes hero_system.png and hero_system-dark.png.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__)) + os.sep
sys.path.insert(0, HERE)

import figstyle  # noqa: E402
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch  # noqa: E402


def system(T):
    fig, ax = plt.subplots(figsize=(figstyle.WIDTH, 3.6))
    ax.set_xlim(0, 96)
    ax.set_ylim(-1.5, 36)
    ax.axis("off")
    UP, DOWN = T["green"], T["gold"]     # sensors out in green, the haptic return in gold

    def box(x, y, w, h, title, sub):
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=1.5",
                                    linewidth=1.4, edgecolor=T["line"], facecolor=T["fill"], zorder=2))
        ax.text(x + w / 2, y + h / 2 + 2.0, title, ha="center", va="center",
                fontsize=figstyle.TITLE, color=T["ink"], fontweight="bold", zorder=3)
        ax.text(x + w / 2, y + h / 2 - 2.6, sub, ha="center", va="center",
                fontsize=figstyle.SMALL, color=T["muted"], zorder=3)

    def arrow(x0, y0, x1, y1, c):
        ax.add_patch(FancyArrowPatch((x0, y0), (x1, y1), arrowstyle="-|>", mutation_scale=13,
                                     linewidth=1.6, color=c, shrinkA=0, shrinkB=0, zorder=1))

    W, H, Y = 23.0, 12.0, 15.0
    TOP = Y + H
    xs = [2.0, 36.5, 71.0]                      # 11.5 of clear gap between boxes
    box(xs[0], Y, W, H, "Watch", "WearOS, wrist")
    box(xs[1], Y, W, H, "Phone", "Android, pocket")
    box(xs[2], Y, W, H, "Server", "any UDP listener")

    # outbound: sensors to the server; labels above the boxes so a wide label cannot land on one
    yo = Y + H * 0.62
    arrow(xs[0] + W, yo, xs[1], yo, UP)
    arrow(xs[1] + W, yo, xs[2], yo, UP)
    for cx, main, sub in (
        ((xs[0] + W + xs[1]) / 2, "Wearable channel", "15 floats"),
        ((xs[1] + W + xs[2]) / 2, "UDP 65000", "30 floats, 120 B, big endian"),
    ):
        ax.text(cx, TOP + 6.0, main, ha="center", fontsize=figstyle.BODY, color=UP, fontweight="bold")
        ax.text(cx, TOP + 2.4, sub, ha="center", fontsize=figstyle.SMALL, color=T["muted"])

    # return: the haptic command reaches the wrist through the phone, not directly
    cw, cp, cs = xs[0] + W / 2, xs[1] + W / 2, xs[2] + W / 2
    y1, y2 = 7.4, 3.2
    ax.plot([cs, cs], [Y, y1], color=DOWN, lw=1.6, zorder=1)
    ax.plot([cp, cs], [y1, y1], color=DOWN, lw=1.6, zorder=1)
    arrow(cp + 2.0, y1, cp, y1, DOWN)
    ax.plot([cp, cp], [y1, Y], color=DOWN, lw=1.6, zorder=1)
    ax.plot([cp, cp], [y1, y2], color=DOWN, lw=1.6, zorder=1)
    ax.plot([cw, cp], [y2, y2], color=DOWN, lw=1.6, zorder=1)
    arrow(cw, y2, cw, Y - 0.4, DOWN)
    ax.text((cp + cs) / 2, y1 + 1.8, "UDP 65010", ha="center",
            fontsize=figstyle.BODY, color=DOWN, fontweight="bold")
    # below the return line, where no vertical run can cross the label
    ax.text((cw + cp) / 2, y2 - 2.4, "haptic command, 3 ints, 12 B, little endian",
            ha="center", va="center", fontsize=figstyle.SMALL, color=T["muted"])
    return fig


if __name__ == "__main__":
    figstyle.save_both(system, HERE + "hero_system")
