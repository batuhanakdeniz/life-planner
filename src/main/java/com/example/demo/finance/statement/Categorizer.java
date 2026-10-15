package com.example.demo.finance.statement;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Açıklamaya göre kural seçer. Önce kullanıcının kuralları, sonra hazır kurallar; her grupta açıklamada
 * geçen en uzun desen kazanır (böylece "UBER EATS" → Restoran, "UBER" → Ulaşım ayrışır).
 */
final class Categorizer {

	private Categorizer() {
	}

	static Optional<CategoryRule> match(String description, List<CategoryRule> rules) {
		var folded = TrText.fold(description);
		var byLength = Comparator.comparingInt((CategoryRule r) -> r.getPattern().length());
		var user = rules.stream().filter(CategoryRule::isUserDefined).filter(r -> folded.contains(r.getPattern())).max(byLength);
		if (user.isPresent()) {
			return user;
		}
		return rules.stream().filter(r -> !r.isUserDefined()).filter(r -> folded.contains(r.getPattern())).max(byLength);
	}
}
