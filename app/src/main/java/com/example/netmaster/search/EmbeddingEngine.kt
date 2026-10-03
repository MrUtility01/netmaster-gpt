package com.example.netmaster.search

import kotlin.math.sqrt

interface EmbeddingEngine{val dimensions:Int;fun embed(text:String):FloatArray;fun cosine(a:FloatArray,b:FloatArray):Float}

/** Dependency-free embedding for offline baseline: signed hashing over tokens, n-grams and technical symbols. */
class HashedEmbeddingEngine(override val dimensions:Int=384):EmbeddingEngine{
    override fun embed(text:String):FloatArray{val v=FloatArray(dimensions);val n=normalize(text);val tokens=n.split(Regex("\\s+")).filter{it.isNotBlank()};tokens.forEach{add(v,it,1f)};tokens.windowed(2,1).forEach{add(v,it.joinToString(" "),0.8f)};tokens.forEach{t->if(t.length>=3)for(i in 0..t.length-3)add(v,t.substring(i,i+3),0.45f)};return normalizeVector(v)}
    override fun cosine(a:FloatArray,b:FloatArray):Float{val n=minOf(a.size,b.size);var s=0f;for(i in 0 until n)s+=a[i]*b[i];return s}
    private fun add(v:FloatArray,t:String,w:Float){val h=t.hashCode();val idx=(h and Int.MAX_VALUE)%v.size;v[idx]+=if(((h ushr 31) and 1)==0)w else -w}
    private fun normalizeVector(v:FloatArray):FloatArray{var n=0.0;v.forEach{n+=it*it};val d=sqrt(n).toFloat();if(d>0f)for(i in v.indices)v[i]/=d;return v}
    private fun normalize(s:String)=s.lowercase().replace('ي','ی').replace('ك','ک').replace(Regex("[^\\p{L}\\p{N}._:/-]+")," ")
}
