package io.github.nexalloy.morphe.youtube.misc.fix.padding

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

internal object PageBottomMarginFingerprint : Fingerprint(
    returnType = "V",
    // 21.46.522 added a boolean parameter (keyboard-visible flag).
    parameters = listOf("Landroid/graphics/Rect;", "Z"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->findFocus()Landroid/view/View;"),
        methodCall(
            smali = "Landroid/view/View;->onCheckIsTextEditor()Z",
            location = MatchAfterWithin(5)
        ),
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = "Landroid/graphics/Rect;",
            name = "bottom"
        )
    )
)

@RequireAppVersion(minVersion = "20.26.000")
@StrictQuery
internal object FeedListPaddingFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/support/v7/widget/RecyclerView;",
            name = "setClipToPadding"
        ),
        methodCall(
            definingClass = "Landroid/support/v7/widget/RecyclerView;",
            name = "setPadding",
            location = MatchAfterImmediately()
        )
    )
)


/**
 * Sets the bottom padding of the list of a browse page laid out behind the navigation bar,
 * to the navigation bar height plus the bottom window inset, once the window insets are known.
 */
@StrictQuery
internal object BrowseListBottomPaddingFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/support/v7/widget/RecyclerView;",
            name = "getPaddingRight"
        ),
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = "Landroid/graphics/Rect;",
            name = "bottom",
            location = MatchAfterWithin(6)
        ),
        methodCall(
            definingClass = "Landroid/support/v7/widget/RecyclerView;",
            name = "setPadding",
            location = MatchAfterWithin(3)
        )
    )
)

internal val browseListSetPaddingMethod = findMethodDirect {
    BrowseListBottomPaddingFingerprint.instructionMatches.last().instruction.methodRef!!
}


internal val pageBottomMarginConstructor = findMethodDirect {
    val match = PageBottomMarginFingerprint.match()
    match.method.instructions.drop(match.instructionMatches.last().index + 1).first {
        it.methodRef?.name == "<init>" &&
                it.methodRef?.paramTypes?.map { it.descriptor } == listOf("I", "I", "[B")
    }.methodRef!!
}

internal val pageKeyboardInsetsMethod = findMethodDirect {
    val match = PageBottomMarginFingerprint.match()
    val instructions = match.method.instructions
    // In 21.46 the keyboard-inset helper (a static method taking a View and
    // returning the inset height as int) moved to the branch right after the
    // Rect.bottom fallback, instead of sitting between onCheckIsTextEditor
    // and Rect.bottom. Take the first such static call after Rect.bottom.
    instructions.subList(
        match.instructionMatches.last().index + 1,
        instructions.size
    ).first {
            it.opcode in listOf(Opcode.INVOKE_STATIC.opCode, Opcode.INVOKE_STATIC_RANGE.opCode) &&
                    it.methodRef?.paramTypes?.map { it.descriptor } == listOf("Landroid/view/View;")
        }.methodRef!!
}
