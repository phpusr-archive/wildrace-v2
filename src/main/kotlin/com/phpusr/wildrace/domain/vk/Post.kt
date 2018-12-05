package com.phpusr.wildrace.domain.vk

import org.hibernate.validator.constraints.Length
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import java.util.*
import javax.persistence.*

/**
 * Класс для хранения записей со стены группы
 */
@Entity
data class Post(
        @field:Id
        val id: Long,

        /** Порядковый номер */
        val number: Int?,

        /** Статус обработки поста */
        @field:Column(name = "status")
        val statusId: Int,

        /** Автор записи */
        @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "from_id")
        val from: Profile,

        /** Дата и время публикации записи */
        val date: Date,

        /** Текст записи */
        @field:Length(max = 1000, message = "text_too_long")
        val text: String,

        /** Hash текста (MD5) */
        @field:Length(max = 32, message = "text_hash_too_long")
        val textHash: String,

        /** Дистанция пробежки */
        val distance: Int?,

        /** Сумма дистанций пробежек */
        val sumDistance: Int?,

        /** Причина редактирования */
        @field:Length(max = 255, message = "edit_reason_too_long")
        val editReason: String?,

        /** Дата последнего редактирования */
        val lastUpdate: Date?
)

interface PostRepo : PagingAndSortingRepository<Post, Long> {

    @Query(value = "select p from Post p left join fetch p.from " +
            "where (:statusId is null OR p.statusId = :statusId) AND (:manualEditing is null OR p.lastUpdate is not null)",
            countQuery = "select count(id) from Post p " +
                "where (:statusId is null OR p.statusId = :statusId) AND (:manualEditing is null OR p.lastUpdate is not null)")
    fun findAll(pageable: Pageable, @Param("statusId") statusId: Int?, @Param("manualEditing") manualEditing: Boolean?): Page<Post>

    @Query("select count(id) from Post where (:statusId is null OR statusId = :statusId) AND (:manualEditing is null OR lastUpdate is not null)")
    fun count(@Param("statusId") statusId: Int?, @Param("manualEditing") manualEditing: Boolean?): Long

    @Query("from Post " +
            "where number is not null AND distance is not null AND sumDistance is not null " +
            "AND date = (select max(date) from Post)")
    fun findLastPost(): Post
}