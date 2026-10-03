package com.example.netmaster.search

import com.example.netmaster.data.*

data class GlobalSearchHit(val kind:String,val id:String,val title:String,val snippet:String,val score:Int)

class GlobalSearchEngine(private val lessonSearch:SearchEngine=SearchEngine()) {
    fun search(lessons:List<Lesson>,notes:List<NoteEntity>,incidents:List<IncidentEntity>,configs:List<ConfigSnapshotEntity>,captures:List<PacketCaptureEntity>,query:String,limit:Int=40):List<GlobalSearchHit>{
        val q=query.trim().lowercase();if(q.isBlank())return emptyList()
        val out=mutableListOf<GlobalSearchHit>()
        lessonSearch.search(lessons,q).take(limit).forEach{out+=GlobalSearchHit("LESSON",it.lesson.id,it.lesson.title,it.matched.joinToString(" • "),it.score+20)}
        notes.forEach{n->score("NOTE",n.id.toString(),n.title,n.body,q,out)}
        incidents.forEach{i->score("INCIDENT",i.id.toString(),i.title,"${i.status} • ${i.severity}\n${i.symptom}\n${i.rootCause}",q,out)}
        configs.forEach{c->score("CONFIG",c.id.toString(),"${c.deviceId} • ${c.label}",c.content.take(300),q,out)}
        captures.forEach{c->score("CAPTURE",c.id.toString(),c.name,"${c.format} • ${c.protocolSummary}",q,out)}
        return out.sortedByDescending{it.score}.take(limit)
    }
    private fun score(kind:String,id:String,title:String,body:String,q:String,out:MutableList<GlobalSearchHit>){val t=(title+" "+body).lowercase();val hits=q.split(Regex("\\s+")).count{it.isNotBlank()&&t.contains(it)};if(hits>0)out+=GlobalSearchHit(kind,id,title,body.replace('\n',' ').take(220),hits*10+if(title.lowercase().contains(q))20 else 0)}
}
