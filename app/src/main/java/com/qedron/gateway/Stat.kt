package com.qedron.gateway

data class Stat(
    val today:String?=null,
    val last:String?=null,
    val message:String?=null,
    val count:Int=-1,
    val sent:Int=-1,
    val delivered:Int=-1,
    val failed:Int=-1)
