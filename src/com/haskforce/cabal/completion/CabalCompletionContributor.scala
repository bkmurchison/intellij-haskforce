package com.haskforce.cabal.completion

import com.haskforce.cabal.CabalLanguage
import com.haskforce.cabal.lang.psi
import com.haskforce.cabal.lang.psi.CabalPsiUtil
import com.haskforce.constants.GhcLanguageExtensions
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext

final class CabalCompletionContributor extends CompletionContributor {

  // Extends this class with completion for Cabal files.
  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement().withLanguage(CabalLanguage.INSTANCE),
    newProvider()
  )

  private def newProvider() = new CompletionProvider[CompletionParameters]() {
    override def addCompletions
        (parameters: CompletionParameters,
         context: ProcessingContext,
         result: CompletionResultSet)
        : Unit
        = new CompletionWrapper(parameters, result).run()
  }

  private class CompletionWrapper(
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {

    def run(): Unit = {
      if (completeExtensions()) return
    }

    lazy val position: PsiElement = parameters.getPosition

    def completeExtensions(): Boolean = {
      CabalPsiUtil.getFieldContext(position).collect {
        case el: psi.impl.ExtensionsImpl => filterExtensions(el).foreach { result.addElement }
      }.isDefined
    }

    /** Skip already provided extensions or their negation. */
    private def filterExtensions(el: psi.impl.ExtensionsImpl): Iterator[LookupElement] = {
      val currentExts = el.getValue.toSet.flatMap(GhcLanguageExtensions.get)
      val negExts = currentExts.flatMap(GhcLanguageExtensions.negate)
      val skipExts = (currentExts ++ negExts).map(_.toString)
      GhcLanguageExtensions.asLookupElements.iterator.filter(x => !skipExts.contains(x.getLookupString))
    }
  }
}
