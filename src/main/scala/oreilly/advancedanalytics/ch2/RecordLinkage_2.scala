package oreilly.advancedanalytics.ch2

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import java.lang.Double.isNaN

// https://github.com/sryza/aas/blob/master/ch02-intro/src/main/scala/com/cloudera/datascience/intro/RunIntro.scala

case class MatchData(id1: Int, id2: Int, scores: Array[Double], matched: Boolean)

object RecordLinkage_2 extends Serializable {

	def main(args: Array[String]): Unit = {
	  
		val sc = new SparkContext("local", "RecordLinkage_2", System.getenv("SPARK_HOME"))
	  
		val rawblocks = sc.textFile("../../Spark/workspace_tuts/Advanced_Analytics_with_Spark_workspace_1/linkage")
		
		val rawblocks_take_10 = rawblocks.take(10)
		println("---- rawblocks_take_10")
		rawblocks_take_10.foreach(println)
	  
		val sampleLine = rawblocks_take_10(5)
		val samplePieces = sampleLine.split(',')
	  
		println("==== sampleLine samplePieces:")
		for(i <- 0 until samplePieces.length) {
			println(i + ": " + samplePieces(i))
		}
	  
		val noheader = rawblocks.filter(!isHeader(_))
	  
		val parsed = noheader.map(line => parse(line))
				.filter(line => (Int.MinValue != line.id1) && (Int.MinValue != line.id2))
				// filtering out all id NumberFormatExceptions
		parsed.cache()
		val matchCounts = parsed.map(md => md.matched).countByValue
		val matchCountsSeq = matchCounts.toSeq
		
		// Histogram
		matchCountsSeq.sortBy(_._2).foreach(println)
		
		// ------ stats ------------------------- //
		
		val stats = (0 until 9).map(i => {
			parsed.map(md => md.scores(i)).filter(!isNaN(_)).stats()
		})
		stats.foreach(println)

		val nasRDD = parsed.map(md => {
			md.scores.map(d => NAStatCounter(d))
		})
		val reduced = nasRDD.reduce((n1, n2) => {
			n1.zip(n2).map { case (a, b) => a.merge(b) }
		})
		reduced.foreach(println)
		
		// THIS IS BLOWING UP -- java.util.NoSuchElementException: next on empty iterator
		val statsm = statsWithMissing(parsed.filter(_.matched).map(_.scores)) 
		val statsn = statsWithMissing(parsed.filter(!_.matched).map(_.scores))
		
		println("======== statsWithMissing function call =======")
		
		statsm.zip(statsn).map { case(m, n) =>
			(m.missing + n.missing, m.stats.mean - n.stats.mean)
		}.foreach(println)
		
		
	} // end main
	
	def isHeader(line: String) = line.contains("id_1")
	  
	def parse(line: String) = {
		val pieces = line.split(',')
		val id1 = toInt(pieces,0)
		val id2 = toInt(pieces,1)
		val scores = pieces.slice(2, 11).map(toDouble)
		val matched = toBoolean(pieces,11)
		MatchData(id1, id2, scores, matched)
	}
	
	// Runs a couple of iterations and then
	// GETTING java.util.NoSuchElementException: next on empty iterator
	def statsWithMissing(rdd: RDD[Array[Double]]): Array[NAStatCounter] = {
		val nastats = rdd.mapPartitions((iter: Iterator[Array[Double]]) => {
			// check iter not empty!
				val nas: Array[NAStatCounter] = iter.next().map(d => {
					NAStatCounter(d)
				})
				iter.foreach(arr => {
					nas.zip(arr).foreach { case (n, d) => n.add(d) }
				})
				Iterator(nas)
		})
		nastats.reduce((n1, n2) => {
			n1.zip(n2).map { case (a, b) => a.merge(b) }
		})
	}
	
	/* ----- Parsing helpers ----- */
	
	def toDouble(s: String): Double = {
		try {
			if ("?".equals(s)) Double.NaN
			else s.toDouble
		} catch { 
		  	case nfe: NumberFormatException => Double.NaN
			case aiobe: ArrayIndexOutOfBoundsException => Double.NaN
		}
	}
	
	def toInt(s: Array[String], index: Int): Int = {
		try {
			s(index).toInt
		} catch {
		  	case nfe: NumberFormatException => Int.MinValue
		  	case aobe: ArrayIndexOutOfBoundsException => Int.MinValue
		}
	}
	
	def toBoolean(s: Array[String], index: Int): Boolean = {
		try {
			s(index).toBoolean
		} catch {
		  	case aiobe: ArrayIndexOutOfBoundsException => false
		  	case iae: IllegalArgumentException => false
		}
	}
}




