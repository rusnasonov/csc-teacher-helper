package csc_teacher_helper

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.features.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class Comment(val author: String, val date: String)

class IsUserWriteComment(private val name: String, private val submission: Submission) {
    suspend fun check(): Boolean {
        return this.submission
                .comments()
                .filter { comment -> comment.author == this.name }
                .count() > 0
    }
}

class IsUserWriteLastComment(private val name: String, private val submission: Submission) {
    suspend fun check(): Boolean {
        return this.submission
                .comments()
                .last()
                .author == this.name
    }
}

interface Report {
    suspend fun generate(): String
}

class NeedMyReactionReport(private val submissions: List<Submission>, private val me: String): Report {
    override suspend fun generate(): String {
        return this.submissions
                .filter { IsUserWriteComment(this.me, it).check() }
                .filter { !IsUserWriteLastComment(this.me, it).check() }
                .mapIndexed { index, submission ->  "${index+1}. ${submission.string()}" }
                .joinToString("\n", prefix = "Need my reaction:\n")
    }
}

class NeedStudentReactionReport(private val submissions: List<Submission>, private val me: String): Report {
    override suspend fun generate(): String {
        return this.submissions
                .filter { IsUserWriteComment(this.me, it).check() }
                .filter { IsUserWriteLastComment(this.me, it).check() }
                .filter { it.score() == 0 }
                .mapIndexed { index, submission ->  "${index+1}. ${submission.string()}" }
                .joinToString("\n", prefix = "Need student reaction:\n")
    }
}

class NeedStudentSolutionReport(private val submissions: List<Submission>, private val me: String): Report {
    override suspend fun generate(): String {
        return this.submissions
                .filter { it.comments().count() == 0 }
                .mapIndexed { index, submission -> "${index+1}. ${submission.string()}" }
                .joinToString("\n", prefix = "Need student solution:\n")
    }
}

class NeedTeacherAssignmentReport(private val submissions: List<Submission>, private val me: String): Report {
    override suspend fun generate(): String {
        return this.submissions
                .filter { it -> it.comments().distinctBy { it.author }.count() == 1 }
                .mapIndexed { index, submission -> "${index+1}. ${submission.string()}" }
                .joinToString("\n", prefix = "Need teacher assignment:\n")
    }
}

class StudentsWithGradeReport(private val submissions: List<Submission>, private val me: String): Report {
    override suspend fun generate(): String {
        return this.submissions
                .filter { IsUserWriteComment(this.me, it).check() }
                .filter { it.score() > 0 }
                .mapIndexed { index, submission ->  "${index+1}. ${submission.string()}" }
                .joinToString("\n", prefix = "Students with grade:\n")
    }
}

class Submission(private val client: HttpClient, private val url: String) {
    private lateinit var _page: Document

    private suspend fun page(): Document {
        if (!this::_page.isInitialized) {
            val response = this.client.get<String>(url)
            this._page = Jsoup.parse(response)
        }
        return this._page
    }

    suspend fun author(): String {
        return this.page()
                .root()
                .select("#student-submission-comments.container div.row div.col-xs-12.h2-and-buttons h2")
                .map { element -> element.text() }
                .first()
                .replace(this.title(), "")
    }

    suspend fun comments(): List<Comment> {
        return this.page()
                .root()
                .select("div.csc-well.assignment-comment")
                .map { element ->
                    val commentAuthor = element.select("h5.assignment").text()
                    val commentDate = element.select("div.metainfo-holder span.metainfo.pull-right").text()
                    Comment(commentAuthor, commentDate)
                }
                .toList()
    }

    suspend fun score(): Int {
        return this.page()
                .root()
                .select("input#id_score.input-grade.numberinput.form-control")
                .first()
                .attr("value")
                .toIntOrNull() ?: 0
    }

    suspend fun title(): String {
        return this.page()
                .root()
                .select("div#student-submission-comments.container div.row div.col-xs-12.h2-and-buttons h2 small")
                .map { element -> element.text() }
                .first()
    }

    fun link(): String {
       return  this.url
    }

    suspend fun string(): String {
        return "${this.author()} [${this.comments().lastOrNull()?.date ?: "-"}] [score:${this.score()}]: ${this.link()}"
    }
}

class Assignment(private val client: HttpClient, private val id: String) {
    private lateinit var _page: Document

    private val assignmentUrl = "https://my.compscicenter.ru/teaching/assignments"

    private suspend fun page(): Document {
        if (!this::_page.isInitialized) {
            val response = this.client.get<String> {
                url(this@Assignment.assignmentUrl)
                parameter("assignment", this@Assignment.id)
            }
            this._page = Jsoup.parse(response)
        }
        return this._page
    }

    suspend fun submissions(): List<Submission> {
        return this.page()
                .root()
                .select("a")
                .map { it.attr("href") }
                .filter { it.startsWith("${this.assignmentUrl}/submissions")}
                .map { Submission(client, it) }
    }

    suspend fun title(): String {
        return this.page()
                .root()
                .selectFirst("option[selected='']")
                .text()
    }
}

suspend fun main(args: Array<String>) {
    val assignmentId = args[0]
    val client = HttpClient() {
        defaultRequest {
            header("Cookie", "cscsessionid=${System.getenv("CSC_SESSION_ID")}")
        }
    }
    val me = System.getenv("CSC_ME")
    val assignment = Assignment(client, assignmentId)
    val submissions = assignment.submissions()
    val reports = listOf(
            NeedMyReactionReport(submissions, me),
            NeedStudentReactionReport(submissions, me),
            NeedTeacherAssignmentReport(submissions, me),
            StudentsWithGradeReport(submissions, me),
            NeedStudentSolutionReport(submissions, me)
    )
    println(assignment.title())
    reports.forEach{ println(it.generate()); println("-".repeat(20))}
}
