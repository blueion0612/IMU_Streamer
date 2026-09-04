"""Render the system figure used at the top of the README.

    python docs/figures/make_hero.py

Writes hero_system.png and hero_system-dark.png. The dark file is what the
README serves to readers whose browser is in dark mode.
"""
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

THEMES = {
    "light": dict(bg="white", ink="#1c2530", muted="#5b6875", line="#b9c3cf",
                  fill="#eef2f6", up="#4a7fb5", down="#c8683f"),
    "dark": dict(bg="#0d1117", ink="#e6edf3", muted="#9198a1", line="#3d444d",
                 fill="#161b22", up="#6ea8dd", down="#e08a5c"),
}

HERE = os.path.dirname(os.path.abspath(__file__))


def render(theme, out):
    T = THEMES[theme]
    fig, ax = plt.subplots(figsize=(9.6, 3.6), dpi=170)
    ax.set_xlim(0, 96)
    ax.set_ylim(0, 36)
    ax.axis("off")
    fig.patch.set_facecolor(T["bg"])

    def box(x, y, w, h, title, sub):
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=1.5",
                                    linewidth=1.4, edgecolor=T["line"], facecolor=T["fill"], zorder=2))
        ax.text(x + w / 2, y + h / 2 + 2.0, title, ha="center", va="center",
                fontsize=12, color=T["ink"], fontweight="bold", zorder=3)
        ax.text(x + w / 2, y + h / 2 - 2.6, sub, ha="center", va="center",
                fontsize=9.4, color=T["muted"], zorder=3)

    def arrow(x0, y0, x1, y1, c):
        ax.add_patch(FancyArrowPatch((x0, y0), (x1, y1), arrowstyle="-|>", mutation_scale=13,
                                     linewidth=1.6, color=c, shrinkA=0, shrinkB=0, zorder=1))

    W, H, Y = 23.0, 12.0, 15.0
    TOP = Y + H
    xs = [2.0, 36.5, 71.0]                      # 11.5 of clear gap between boxes
    box(xs[0], Y, W, H, "Watch", "WearOS, wrist")
    box(xs[1], Y, W, H, "Phone", "Android, pocket")
    box(xs[2], Y, W, H, "Server", "any UDP listener")

    # Outbound: sensors to the server. Labels sit above the boxes so that a
    # label wider than the gap cannot land on top of one.
    yo = Y + H * 0.62
    arrow(xs[0] + W, yo, xs[1], yo, T["up"])
    arrow(xs[1] + W, yo, xs[2], yo, T["up"])
    for cx, main, sub in (
        ((xs[0] + W + xs[1]) / 2, "Wearable channel", "15 floats"),
        ((xs[1] + W + xs[2]) / 2, "UDP 65000", "30 floats, 120 B, big endian"),
    ):
        ax.text(cx, TOP + 6.0, main, ha="center", fontsize=9.6, color=T["up"], fontweight="bold")
        ax.text(cx, TOP + 2.4, sub, ha="center", fontsize=8.4, color=T["muted"])

    # Return: the haptic command reaches the wrist through the phone, not directly.
    cw, cp, cs = xs[0] + W / 2, xs[1] + W / 2, xs[2] + W / 2
    y1, y2 = 7.4, 3.2
    ax.plot([cs, cs], [Y, y1], color=T["down"], lw=1.6, zorder=1)      # down from Server
    ax.plot([cp, cs], [y1, y1], color=T["down"], lw=1.6, zorder=1)     # across to Phone
    arrow(cp + 2.0, y1, cp, y1, T["down"])
    ax.plot([cp, cp], [y1, Y], color=T["down"], lw=1.6, zorder=1)      # up into Phone
    ax.plot([cp, cp], [y1, y2], color=T["down"], lw=1.6, zorder=1)     # down again
    ax.plot([cw, cp], [y2, y2], color=T["down"], lw=1.6, zorder=1)     # across to Watch
    arrow(cw, y2, cw, Y - 0.4, T["down"])                              # up into Watch

    ax.text((cp + cs) / 2, y1 + 1.8, "UDP 65010", ha="center",
            fontsize=9.6, color=T["down"], fontweight="bold")
    ax.text((cw + cp) / 2, y2 + 1.8, "haptic command, 3 ints, 12 B, little endian",
            ha="center", fontsize=8.4, color=T["muted"])

    fig.tight_layout(pad=0.2)
    fig.savefig(out, dpi=170, bbox_inches="tight", facecolor=T["bg"])
    plt.close(fig)
    print("wrote", out)


if __name__ == "__main__":
    render("light", os.path.join(HERE, "hero_system.png"))
    render("dark", os.path.join(HERE, "hero_system-dark.png"))
