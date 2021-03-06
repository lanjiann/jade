package org.ucombinator.jade.jvm.classfile

import org.ucombinator.jade.jvm.classfile.TypeCommons.JavaIdentifier

import scala.language.implicitConversions
import scala.util.parsing.combinator.JavaTokenParsers


trait JavaTokenParsersOpt extends JavaTokenParsers {
  // The grammar includes the terminal symbol Identifier to denote the name of a
  // type, field, method, formal parameter, local variable, or type variable, as
  // generated by a Java compiler.
  protected[this] lazy val identifier: Parser[JavaIdentifier] =
    rep1(acceptIf(isSignatureIdentifierCharacter)("identifier expected but `" + _ + "' found")) ^^
      (x => JavaIdentifier(x.mkString))

  // A `rep` that is bracketed by `left` and `right`
  def rep1bra[T](left: => Parser[Any], p: => Parser[T], right: => Parser[Any]): Parser[List[T]] = {
    opt(left ~> rep1(p) <~ right) ^^ (_.getOrElse(Nil))
  }

  def repbra[T](left: => Parser[Any], p: => Parser[T], right: => Parser[Any]): Parser[List[T]] = {
    opt(left ~> rep(p) <~ right) ^^ (_.get)
  }

  implicit def seqToTuple[A,B,Z](f: (A, B) => Z): A ~ B => Z = {
    case a ~ b => f(a, b)
  }

  implicit def seqToTuple[A,B,C,Z](f: (A, B, C) => Z): A ~ B ~ C => Z = {
    case a ~ b ~ c => f(a, b, c)
  }

  implicit def seqToTuple[A,B,C,D,Z](f: (A, B, C, D) => Z): A ~ B ~ C ~ D => Z = {
    case a ~ b ~ c ~ d => f(a, b, c, d)
  }
}

