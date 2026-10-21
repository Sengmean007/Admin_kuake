package org.sengmean.support;

import com.microsoft.playwright.Page;
import org.sengmean.report.CaseLog;

import java.util.List;

/**
 * Creates a rebate program targeted at <b>one Agent group and one Member group</b>, through the
 * back office's own API, using the session the browser is already signed in with.
 *
 * <h2>Why this exists at all</h2>
 * The obvious way to make such a program is the Create form: choose "Optional", pick a group on
 * each side, move it across, submit. That is what {@code RebateProgramFormPage.targetOnly} does
 * and it is the right thing for a UI test.
 *
 * <p>It cannot be used on this environment. The form's two pickers are fed by
 * {@code /api/account/agent} and {@code /api/account/member_group}, and both answer
 * {@code {"status":-10,"message":"Permission denied"}} for the operator the suite signs in as -
 * on Modify as well as Create, and on every currency. Both Option panes therefore render empty,
 * so there is nothing to select and no pairing to express but "All Agents / All Groups".
 *
 * <p>That single available pairing is the root of a whole family of skipped rows, because the
 * back office treats <b>All Agents as covering every agent</b>: it collides with any program
 * that exists, so the suite can hold at most one program at a time and can never produce the
 * <em>second, distinct</em> pairing that the duplicate-pairing rows are about.
 *
 * <h2>What this does instead, and the trade-off</h2>
 * It posts the program directly, with real group ids - the same call the form makes, with the
 * ids filled in. Verified on 2026-09-21: two programs created this way on THB rendered as
 * {@code agents002 / testing} and {@code agent-8001 / Default 00}, and coexisted happily because
 * their pairings differ.
 *
 * <p><b>This is seeding, not testing.</b> A program made here has not exercised the Create form,
 * so it must never be used to satisfy a row about creating - {@code RBMP-011} and its neighbours
 * stay on the UI and report honestly when they cannot run. What it is for is giving the rows
 * that need a <em>pre-existing</em> second pairing something real to work against, which is the
 * ordinary use of a fixture.
 *
 * <p>The moment the operator is granted read access to agents and member groups, this class
 * stops being necessary and the form can do it all.
 */
public final class RebateProgramApi {

    /** The rebate types, as the API numbers them. */
    public static final int DAILY = 1;
    public static final int REAL_TIME = 2;

    private RebateProgramApi() {
    }

    /**
     * Creates a program targeting exactly one Agent group and one Member group.
     *
     * <h2>The request shape was not guessed</h2>
     * The API was asked. A first attempt with {@code agent_ids: [31]} and no type came back with
     * {@code {"status":-4,"message":"Verification wrong","data":{"type":["Type is a required
     * field"],"agent_ids.0.id":["agent_ids.0.id is a required field"]}}} - which names exactly
     * what is missing, so the ids go as {@code [{id: n}]} and the type travels with them.
     *
     * @return the server's own reply, so a caller can report it verbatim rather than paraphrase
     */
    public static String create(Page page, String currency, int type, String name,
                                int agentGroupId, int memberGroupId) {
        Object reply = page.evaluate(
                "async ([currency, type, name, agentId, groupId]) => {"
                        + "  const token = localStorage.getItem('auth._token.account');"
                        + "  const body = {"
                        + "    name: name, type: Number(type),"
                        + "    agent_ids: [{ id: Number(agentId) }],"
                        + "    group_ids: [{ id: Number(groupId) }],"
                        + "    status: 1, game_rates: []"
                        + "  };"
                        + "  const response = await fetch("
                        + "    '/api/account/rebate-programs?currency=' + currency, {"
                        + "      method: 'POST',"
                        + "      headers: {"
                        + "        'Content-Type': 'application/json',"
                        + "        'Accept': 'application/json',"
                        + "        'authorization': token"
                        + "      },"
                        + "      body: JSON.stringify(body)"
                        + "    });"
                        + "  return await response.text();"
                        + "}",
                List.of(currency, String.valueOf(type), name,
                        String.valueOf(agentGroupId), String.valueOf(memberGroupId)));

        String answer = String.valueOf(reply);
        CaseLog.step("Seeded \"" + name + "\" via the API, targeting agent group " + agentGroupId
                + " and member group " + memberGroupId + " on " + currency + ": " + oneLine(answer));
        return answer;
    }

    /** True when the back office accepted the creation. */
    public static boolean wasAccepted(String apiReply) {
        return apiReply.contains("\"status\":200");
    }

    private static String oneLine(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "...";
    }
}
