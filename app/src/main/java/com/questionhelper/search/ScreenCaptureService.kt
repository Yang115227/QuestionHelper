    private fun processBitmap(bitmap: Bitmap) {
        val manager = QuestionApp.ocrManager

        if (!manager.isReady) {
            val error = manager.initError ?: "未知错误"
            Log.e(tag, "OCR 未就绪: $error")
            handler.post {
                Toast.makeText(this, "OCR 未初始化\n$error", Toast.LENGTH_LONG).show()
            }
            bitmap.recycle()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = manager.recognizeFromBitmap(bitmap)
                bitmap.recycle()

                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))
                    
                    val questionText = question?.content ?: text
                    val answerText = question?.answer ?: "未在题库中找到匹配题目"
                    val analysisText = question?.analysis ?: ""
                    val isMatched = question != null

                    // ✅ 改为发送广播显示悬浮结果窗
                    sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
                        putExtra(FloatWindowService.EXTRA_QUESTION, questionText)
                        putExtra(FloatWindowService.EXTRA_ANSWER, answerText)
                        putExtra(FloatWindowService.EXTRA_ANALYSIS, analysisText)
                        putExtra(FloatWindowService.EXTRA_MATCHED, isMatched)
                    })
                } else {
                    handler.post { 
                        Toast.makeText(this@ScreenCaptureService, R.string.ocr_no_text, Toast.LENGTH_SHORT).show() 
                    }
                }
            } catch (e: Throwable) {
                Log.e(tag, "OCR failed", e)
                handler.post { 
                    Toast.makeText(this@ScreenCaptureService, R.string.ocr_failed, Toast.LENGTH_SHORT).show() 
                }
            }
        }
    }
