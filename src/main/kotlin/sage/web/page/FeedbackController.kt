package sage.web.page

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import sage.domain.commons.BadArgumentException
import sage.entity.Feedback
import sage.web.auth.Auth
import javax.servlet.http.HttpServletRequest

@Controller
@RequestMapping("/feedbacks")
class FeedbackController {
  @RequestMapping
  fun show(): ModelAndView {
    val uid = Auth.uid()
    val feedbacks = Feedback.orderBy("id desc").findList()
    return ModelAndView("feedbacks").addObject("feedbacks", feedbacks).addObject("uid", uid)
  }
  
  @RequestMapping("/new", method = arrayOf(RequestMethod.POST))
  fun create(@RequestParam content: String,
             @RequestParam(defaultValue = "") name: String,
             @RequestParam(defaultValue = "") email: String, request: HttpServletRequest): String {
    if (content.isEmpty()) throw BadArgumentException("请输入反馈内容")
    Feedback(content = content, name = name.trim(), email = email.trim(), ip = request.remoteAddr.orEmpty()).save()
    return "redirect:/feedbacks"
  }
}