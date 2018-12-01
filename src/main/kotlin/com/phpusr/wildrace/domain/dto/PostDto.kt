package com.phpusr.wildrace.domain.dto

import com.fasterxml.jackson.annotation.JsonView
import com.phpusr.wildrace.consts.Consts
import com.phpusr.wildrace.domain.Views
import com.phpusr.wildrace.domain.vk.Post
import com.phpusr.wildrace.domain.vk.Profile

@JsonView(Views.PostDtoREST::class)
class PostDto(
        val id: Long,
        val number: Int?,
        val statusId: Int,
        val from: Profile,
        val date: Long,
        val text: String,
        val distance: Int?,
        val sumDistance: Int?,
        val link: String
)

object PostDtoObject {
    fun create(post: Post, groupShortLink: String, groupId: Long): PostDto {
        val link = "${Consts.VKLink}/${groupShortLink}?w=wall${groupId}_${post.id}"
        return PostDto(post.id, post.number, post.statusId, post.from, post.date.time, post.text, post.distance, post.sumDistance, link)
    }
}