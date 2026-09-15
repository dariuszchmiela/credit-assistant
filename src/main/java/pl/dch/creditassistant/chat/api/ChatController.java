package pl.dch.creditassistant.chat.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.dch.creditassistant.chat.application.CreditAssistant;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CreditAssistant creditAssistant;

    public ChatController(CreditAssistant creditAssistant) {
        this.creditAssistant = creditAssistant;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String answer = creditAssistant.chat(request.message());

        return new ChatResponse(answer);
    }
}