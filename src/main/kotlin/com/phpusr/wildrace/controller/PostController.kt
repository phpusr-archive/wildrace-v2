package com.phpusr.wildrace.controller

import com.fasterxml.jackson.annotation.JsonView
import com.phpusr.wildrace.domain.Views
import com.phpusr.wildrace.domain.data.ConfigRepo
import com.phpusr.wildrace.domain.data.TempDataRepo
import com.phpusr.wildrace.domain.vk.Post
import com.phpusr.wildrace.domain.vk.PostRepo
import com.phpusr.wildrace.dto.EventType
import com.phpusr.wildrace.dto.ObjectType
import com.phpusr.wildrace.dto.PostDto
import com.phpusr.wildrace.dto.PostDtoObject
import com.phpusr.wildrace.service.StatService
import com.phpusr.wildrace.util.WsSender
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("post")
class PostController(
        private val postRepo: PostRepo,
        private val statService: StatService,
        private val tempDataRepo: TempDataRepo,
        private val configRepo: ConfigRepo,
        private val wsSender: WsSender
) {

    private val postSender: (EventType, PostDto) -> Unit
        get() = wsSender.getSender(ObjectType.Post, Views.PostDtoREST::class.java)

    @GetMapping
    @JsonView(Views.PostDtoREST::class)
    fun list(
            @PageableDefault(sort = ["date"], direction = Sort.Direction.DESC) pageable: Pageable,
            @RequestParam statusId: Int?,
            @RequestParam manualEditing: Boolean?
    ): Map<String, Any> {
        val page = postRepo.findAll(pageable, statusId, manualEditing)
        val config = configRepo.get()
        val list = page.content.map { PostDtoObject.create(it, config) }

        return mapOf("list" to list, "totalElements" to page.totalElements)
    }

    @GetMapping("getStat")
    fun getStat(): Map<String, Any> {
        val lastPost = statService.getOneRunning(Sort.Direction.DESC)

        return mapOf(
                "sumDistance" to (lastPost?.sumDistance ?: 0),
                "numberOfRuns" to (lastPost?.number ?: 0),
                "numberOfPosts" to postRepo.count()
        )
    }

    @GetMapping("getLastSyncDate")
    fun getLastSyncDate(): Long {
        return tempDataRepo.get().lastSyncDate.time
    }

    @GetMapping("{id}")
    @JsonView(Views.PostDtoREST::class)
    fun get(@PathVariable("id") post: Post): PostDto {
        return PostDtoObject.create(post)
    }

    @PutMapping("{id}")
    @JsonView(Views.PostDtoREST::class)
    fun update(@RequestBody postDto: PostDto): PostDto? {
        val post = postRepo.findById(postDto.id)
        val newPost = post.orElseThrow{ RuntimeException("post_not_found") }.copy(
                number = postDto.number,
                statusId = postDto.statusId,
                distance = postDto.distance,
                sumDistance = postDto.sumDistance,
                editReason = postDto.editReason,
                lastUpdate = Date()
        )
        postRepo.save(newPost)
        val newPostDto = PostDtoObject.create(newPost, configRepo.get())
        postSender(EventType.Update, newPostDto)

        return newPostDto
    }

    @DeleteMapping("{id}")
    fun delete(@PathVariable("id") post: Post): Long {
        postRepo.deleteById(post.id)
        postSender(EventType.Remove, PostDtoObject.create(post))

        return post.id
    }

}