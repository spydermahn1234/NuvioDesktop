package com.spydermahn.nuvioremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/*
 * Checks (Nov 16): where the answers wait (the phone's own storage, until they are in TESTING.md) and how TESTING.md is
 * read from and written to GitHub (the contents API on Spyder). Reading needs no token, the repo being public; writing
 * needs a fine-grained token with Contents read and write on this one repo, kept in the app's private storage.
 */

class ChecksStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nuvio_checks", Context.MODE_PRIVATE)

    fun answers(): List<CheckAnswer> = try {
        val array = JSONArray(prefs.getString(KEY_ANSWERS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val verdict = runCatching { Verdict.valueOf(o.getString("verdict")) }.getOrNull() ?: return@mapNotNull null
            CheckAnswer(o.getString("section"), o.getString("text"), verdict, o.optString("note"), o.optString("time"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveAnswers(list: List<CheckAnswer>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("section", it.section).put("text", it.text).put("verdict", it.verdict.name).put("note", it.note).put("time", it.time))
        }
        prefs.edit().putString(KEY_ANSWERS, array.toString()).apply()
    }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    private companion object {
        const val KEY_ANSWERS = "answers"
        const val KEY_TOKEN = "github_token"
    }
}

/** TESTING.md as GitHub has it now: the text and the blob's sha (needed to write it back). */
data class ChecklistFile(val markdown: String, val sha: String)

class GitHubError(val code: Int, message: String) : Exception(message)

object ChecklistGitHub {
    private const val REPO = "spydermahn1234/NuvioDesktop"
    private const val BRANCH = "Spyder"
    private const val PATH = "TESTING.md"
    private const val URL_FILE = "https://api.github.com/repos/$REPO/contents/$PATH"
    const val NEW_TOKEN_PAGE = "https://github.com/settings/personal-access-tokens/new"

    fun fetch(token: String): ChecklistFile {
        val c = open("$URL_FILE?ref=$BRANCH", token)
        try {
            val code = c.responseCode
            if (code != 200) throw GitHubError(code, problem(code, writing = false))
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val bytes = Base64.getMimeDecoder().decode(json.getString("content"))
            return ChecklistFile(String(bytes, Charsets.UTF_8), json.getString("sha"))
        } finally {
            c.disconnect()
        }
    }

    /** Writes [markdown] over the version [sha]; a 409 means someone (usually a Claude session) pushed in between. */
    fun write(token: String, markdown: String, sha: String, message: String) {
        val c = open(URL_FILE, token).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = JSONObject()
                .put("message", message)
                .put("content", Base64.getEncoder().encodeToString(markdown.toByteArray(Charsets.UTF_8)))
                .put("sha", sha)
                .put("branch", BRANCH)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code != 200 && code != 201) throw GitHubError(code, problem(code, writing = true))
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, token: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "NuvioRemote")
        if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
    }

    private fun problem(code: Int, writing: Boolean) = when (code) {
        401 -> "GitHub didn't accept the token. Paste a fresh one under GitHub."
        403 -> if (writing) "The token can't write to the repo. It needs Contents: Read and write on NuvioDesktop." else "GitHub is limiting requests. Try again in a few minutes."
        404 -> if (writing) "The token can't see the repo. Give it access to spydermahn1234/NuvioDesktop." else "TESTING.md wasn't found on Spyder."
        409, 422 -> "TESTING.md changed while saving."
        else -> "GitHub answered $code."
    }
}
