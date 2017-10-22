package demo

import org.slf4j.LoggerFactory
import java.util.*

enum class TokenType {
    DICE, NUMBER, OPERATOR
}

data class Token(
        val type: TokenType,
        val value: String
)

data class DndCalculatorResult(
        val min: Int,
        val max: Int,
        val average: Double,
        val generated: Int,
        val text: String
) {
    operator fun plus(other: DndCalculatorResult): DndCalculatorResult {
        return DndCalculatorResult(
                min = this.min + other.min,
                max = this.max + other.max,
                average = this.average + other.average,
                generated = this.generated + other.generated,
                text = this.text + "+" + other.text
        )
    }

    operator fun minus(other: DndCalculatorResult): DndCalculatorResult {
        return DndCalculatorResult(
                min = this.min - other.min,
                max = this.max - other.max,
                average = this.average - other.average,
                generated = this.generated - other.generated,
                text = this.text + "-" + other.text
        )
    }
}

class DndCalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Parses a text [request] as a DnD dice formula (like "3d8 + 1"),
     * and calculates min, max, average values and a generated random sample
     * for the given formula.
     *
     * Any non-numeric letter is treated as "d", other symbols are ignored.
     *
     * @throws RuntimeException if formula is incorrect
     */
    fun calculate(request: String): DndCalculatorResult {
        val normalizedRequest = request
                // remove all spaces and unknown characters
                .replace(Regex("[^a-zA-Zа-яА-Я0-9+-]"), "")
                // replace all words with "d"
                .replace(Regex("[a-zA-Zа-яА-Я]+"), "d")

        val tokens = LinkedList(tokenize(normalizedRequest))
        log.info("Tokenized request: " + tokens)
        return Parser(object: TokenStream {
            override fun read(): Token? {
                return tokens.pollFirst()
            }

            override fun unread(token: Token) {
                return tokens.add(0, token)
            }
        }).parse()
    }

    /**
     * Simple interface to represent a stream of tokens,
     * that is capable of reading the next token, and unreading
     * a token (since the parser has to check the next token,
     * but to put it back sometimes).
     *
     * Returns null when the end of the stream is reached.
     */
    private interface TokenStream {
        fun read(): Token?
        fun unread(token: Token)
    }

    /**
     * Splits a request string in a list of separate tokens.
     */
    private fun tokenize(request: String): List<Token> {
        val result = mutableListOf<Token>()
        var currentType: TokenType? = null
        val accumulator = StringBuilder()

        val finishCurrentToken = {
            result.add(Token(currentType!!, accumulator.toString()))
            accumulator.setLength(0)
        }

        for (char in request) {
            when(char) {
                '+', '-' -> {
                    currentType ?: throw RuntimeException("Unary operator ${char} at the start of request is not supported")
                    finishCurrentToken()

                    currentType = TokenType.OPERATOR
                    accumulator.append(char)
                }
                in '0'..'9' -> {
                    when (currentType) {
                        TokenType.DICE, TokenType.NUMBER ->
                            accumulator.append(char)
                        TokenType.OPERATOR -> {
                            finishCurrentToken()

                            currentType = TokenType.NUMBER
                            accumulator.append(char)
                        }
                        null -> {
                            currentType = TokenType.NUMBER
                            accumulator.append(char)
                        }
                    }
                }
                'd' -> {
                    if (currentType != null) {
                        finishCurrentToken()
                    }
                    currentType = TokenType.DICE
                }
                else -> {
                    throw RuntimeException("Unexpected character: ${char}")
                }
            }
        }
        if (currentType != null) {
            finishCurrentToken()
        } else {
            throw RuntimeException("Empty request")
        }
        return result
    }

    /**
     * Parser that parses the stream of tokens and calculates
     * dnd result along the way.
     */
    private class Parser(val stream: TokenStream) {
        fun parse(): DndCalculatorResult {
            return readExpression()
        }
        private val random = Random()
        private fun readExpression(): DndCalculatorResult {
            val token = stream.read()
            val leftExpr = when (token?.type) {
                TokenType.DICE -> diceResult(token.value)
                TokenType.NUMBER -> {
                    val nextToken = stream.read()
                    if (nextToken?.type == TokenType.DICE) {
                        diceResult(nextToken.value, token.value.toInt())
                    } else {
                        if (nextToken != null) stream.unread(nextToken)
                        numberResult(token.value)
                    }
                }
                TokenType.OPERATOR -> throw RuntimeException("Unexpected operator: ${token.value}")
                null -> throw RuntimeException("Unexpected end of expression")
            }

            val operatorToken = stream.read()
            return when (operatorToken?.type) {
                TokenType.DICE,
                TokenType.NUMBER ->
                    throw RuntimeException("Unexpected ${operatorToken.type} expression after expression with no operator")
                TokenType.OPERATOR -> {
                    val rightExpression = readExpression()
                    when (operatorToken.value) {
                        "+" -> leftExpr + rightExpression
                        "-" -> leftExpr - rightExpression
                        else -> throw RuntimeException("Unsupported operator ${operatorToken.value}")
                    }
                }
                null -> leftExpr
            }
        }

        /**
         * Generates an (intermediary) result for a dice formula part, like "2d8"
         *
         * @param value formula number part, e.g., "8" for "2d8"
         * @param multiplier forumula multiplier, e.g. 2 for "2d8"
         * @return [DndCalculatorResult] for a given dice formula part
         */
        private fun diceResult(value: String, multiplier: Int = 1): DndCalculatorResult {
            if (multiplier > 1000 || multiplier < 1) throw RuntimeException("Incorrect multiplier ${multiplier}")
            val diceFaces = value.toInt()
            if (diceFaces > 1000 || diceFaces < 1) throw RuntimeException("Incorrect dice faces count ${diceFaces}")
            val min = multiplier
            val max = diceFaces * multiplier
            val average = (min + max) / 2.0
            val generated = (1..multiplier).sumBy {
                random.nextInt(diceFaces) + 1
            }

            val normalizedText = if (multiplier == 1) {
                "d${value}"
            } else {
                "${multiplier}d${value}"
            }
            return DndCalculatorResult(min, max, average, generated, normalizedText)
        }

        /**
         * Generates an (intermediary) result for a constant number formula part,
         * like "3".
         *
         * @param value a string representation of a number, i.e. 3
         * @return [DndCalculatorResult] that represents a constant number formula
         */
        private fun numberResult(value: String): DndCalculatorResult {
            val number = value.toInt()
            if (number < 1 || number > 1000) throw RuntimeException("Unsupported number: ${number}")
            return DndCalculatorResult(
                    min = number,
                    max = number,
                    average = number.toDouble(),
                    generated = number,
                    text = number.toString()
            )
        }
    }
}