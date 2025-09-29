package com.example

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 100)
    val role = enumerationByName("role", 50, UserRole::class).default(UserRole.USER)
    override val primaryKey = PrimaryKey(id)
}