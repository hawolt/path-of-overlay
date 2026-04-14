package com.hawolt.guide;

import com.hawolt.data.GemRequirements;
import com.hawolt.data.MappingConfig;
import com.hawolt.data.RewardsConfig;
import com.hawolt.guide.model.GuideStep;
import com.hawolt.guide.model.Segment;
import com.hawolt.logger.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GemAnnotator {

    public record DisplayEntry(GuideStep step, int rawIndex) {
    }

    private final MappingConfig mappingConfig;
    private final RewardsConfig rewardsConfig;

    public GemAnnotator(MappingConfig mappingConfig, RewardsConfig rewardsConfig) {
        this.mappingConfig = mappingConfig;
        this.rewardsConfig = rewardsConfig;
    }

    public List<DisplayEntry> buildDisplayList(
            List<GuideStep> rawSteps,
            GemRequirements gemRequirements,
            String activeBandit
    ) {
        List<DisplayEntry> displayList = new ArrayList<>();
        Set<String> announcedGems = new HashSet<>();

        for (int rawIndex = 0; rawIndex < rawSteps.size(); rawIndex++) {
            GuideStep rawStep = rawSteps.get(rawIndex);

            if (rawStep.isBanditStep() && rawStep.getBanditRequirements()
                    .stream()
                    .noneMatch(requirement -> requirement.equalsIgnoreCase(activeBandit))) {
                continue;
            }

            if (!rawStep.isQuestStep() || gemRequirements.isEmpty()) {
                displayList.add(new DisplayEntry(rawStep, rawIndex));
                continue;
            }

            String questName = rawStep.getQuestName();
            int actNumber = rawStep.getActNumber();

            GuideStep annotatedQuestStep = annotateWithRewardGem(
                    rawStep,
                    questName,
                    gemRequirements,
                    announcedGems
            );
            displayList.add(new DisplayEntry(annotatedQuestStep, rawIndex));

            GuideStep vendorStep = buildVendorStep(
                    questName,
                    actNumber,
                    gemRequirements,
                    announcedGems,
                    rawStep.getBanditRequirements()
            );
            if (vendorStep != null) {
                displayList.add(new DisplayEntry(vendorStep, rawIndex));
            }
        }

        Logger.info(
                "[GemAnnotator] Built display list: {} raw -> {} display steps (bandit={})",
                rawSteps.size(),
                displayList.size(),
                activeBandit
        );
        return displayList;
    }

    private GuideStep annotateWithRewardGem(
            GuideStep rawStep,
            String questName,
            GemRequirements gemRequirements,
            Set<String> announcedGems
    ) {
        String gemName = rewardsConfig.findFirstRewardGem(questName, gemRequirements, announcedGems);
        if (gemName == null) return rawStep;

        announcedGems.add(gemName);
        List<Segment> segments = new ArrayList<>(rawStep.getSegments());
        segments.add(Segment.lineBreak());
        segments.add(Segment.text("Pick ", null));
        appendGemWithSupportTarget(segments, gemName, gemRequirements);
        return new GuideStep(
                segments,
                rawStep.getQuestName(),
                rawStep.getActNumber(),
                rawStep.getBanditRequirements()
        );
    }

    private GuideStep buildVendorStep(
            String questName,
            int actNumber,
            GemRequirements gemRequirements,
            Set<String> announcedGems,
            Set<String> banditRequirements
    ) {
        List<String> vendorGems = rewardsConfig.findVendorGems(questName, gemRequirements, announcedGems);
        if (vendorGems.isEmpty()) return null;

        announcedGems.addAll(vendorGems);

        String vendorName = mappingConfig.resolveGemVendor(questName, actNumber);
        if (vendorName == null) vendorName = "Vendor";

        List<Segment> segments = new ArrayList<>();
        segments.add(Segment.text("You can now purchase these from ", null));
        segments.add(Segment.text(vendorName, mappingConfig.colorForType("npc")));

        for (String gemName : vendorGems) {
            segments.add(Segment.lineBreak());
            segments.add(Segment.text("- ", null));
            appendGemWithSupportTarget(segments, gemName, gemRequirements);
        }

        return new GuideStep(segments, null, 0, banditRequirements);
    }

    private void appendGemWithSupportTarget(
            List<Segment> segments,
            String gemName,
            GemRequirements gemRequirements
    ) {
        segments.add(Segment.text(gemName, mappingConfig.colorForGem(gemName)));
        String supportTarget = gemRequirements.getSupportTarget(gemName);
        if (supportTarget != null) {
            segments.add(Segment.text(" for ", null));
            segments.add(Segment.text(supportTarget, mappingConfig.colorForGem(supportTarget)));
        }
    }
}