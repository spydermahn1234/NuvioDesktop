package com.nuvio.app.features.whatsnew

/**
 * Every feature worth a card, oldest first. Adding an entry is part of shipping a feature: give it
 * the next number (one more than the highest here) and a pitch a person would stop for, in one
 * sentence. This list is plain Kotlin rather than string resources on purpose: the resources are
 * shared with upstream in many languages and conflict on every merge, and this file is the fork's own.
 *
 * Empty until the first real entries land.
 */
internal val whatsNewEntries: List<WhatsNewEntry> = emptyList()
