package com.cosmicplayer.stafftracker;

import com.cosmicplayer.stafftracker.InteractionLog.Interaction;
import com.cosmicplayer.stafftracker.InteractionLog.LoggedMessage;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches incoming chat for private messages and keeps the last thirty
 * minutes of them. The server echoes both directions in the same
 * format, for example:
 *
 *   [celestial] [BotDoesAFK to me] Hi
 *   [aether] [me to BotDoesAFK] Hi
 *
 * The bracketed word in front is the sender's current planet.
 */
public final class MessageWatcher {
    private static final Pattern PRIVATE_MESSAGE =
            Pattern.compile("^\\[(\\w+)] \\[(?:me to (\\w{1,16})|(\\w{1,16}) to me)] (.*)$");
    private static final long WINDOW_MS = 30 * 60 * 1000;

    /** One parsed private message. Player is the other side of the conversation. */
    private record PrivateMessage(long time, String planet, String player, boolean outgoing, String text) {}

    private static final ArrayDeque<PrivateMessage> recent = new ArrayDeque<>();

    private MessageWatcher() {
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                parse(message.getString());
            }
        });
    }

    private static void parse(String line) {
        Matcher matcher = PRIVATE_MESSAGE.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        boolean outgoing = matcher.group(2) != null;
        String player = outgoing ? matcher.group(2) : matcher.group(3);
        recent.addLast(new PrivateMessage(System.currentTimeMillis(), matcher.group(1), player, outgoing, matcher.group(4)));
        dropExpired();
    }

    /**
     * Snapshot for a count press: the player last replied to, both planets,
     * and the recent conversation with them. The player is null when
     * nothing was replied to in the last thirty minutes.
     */
    public static Interaction capture() {
        dropExpired();
        long now = System.currentTimeMillis();

        PrivateMessage lastReply = null;
        for (PrivateMessage message : recent) {
            if (message.outgoing) {
                lastReply = message;
            }
        }
        if (lastReply == null) {
            return new Interaction(now, null, null, null, List.of());
        }

        String player = lastReply.player;
        String playerPlanet = null;
        List<LoggedMessage> conversation = new ArrayList<>();
        for (PrivateMessage message : recent) {
            if (!message.player.equalsIgnoreCase(player)) {
                continue;
            }
            conversation.add(new LoggedMessage(message.time, message.outgoing, message.planet, message.text));
            if (!message.outgoing) {
                playerPlanet = message.planet;
            }
        }

        // Counted messages leave the buffer. Counting the same player again
        // later logs just the new conversation instead of repeating this one.
        recent.removeIf(message -> message.player.equalsIgnoreCase(player));
        return new Interaction(now, player, lastReply.planet, playerPlanet, conversation);
    }

    private static void dropExpired() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!recent.isEmpty() && recent.peekFirst().time < cutoff) {
            recent.removeFirst();
        }
    }
}
