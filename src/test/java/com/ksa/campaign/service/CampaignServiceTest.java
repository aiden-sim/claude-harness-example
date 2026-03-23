package com.ksa.campaign.service;

import com.ksa.campaign.domain.CampaignFixture;
import com.ksa.campaign.exception.CampaignNotFoundException;
import com.ksa.campaign.exception.InvalidParameterException;
import com.ksa.campaign.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CampaignService campaignService;

    @Test
    void getCampaigns_defaultPaging() {
        given(campaignRepository.findCampaigns(eq(1L), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 21))))
                .willReturn(List.of());
        given(campaignRepository.countCampaigns(eq(1L), isNull(), isNull()))
                .willReturn(0L);

        var result = campaignService.getCampaigns(1L, null, null, null, null);

        assertThat(result.data()).isEmpty();
        assertThat(result.paging().hasNext()).isFalse();
        assertThat(result.paging().totalCount()).isZero();
    }

    @Test
    void getCampaigns_hasNext_whenMoreResults() {
        var campaigns = List.of(
                CampaignFixture.create(3L), CampaignFixture.create(2L), CampaignFixture.create(1L)
        );
        given(campaignRepository.findCampaigns(eq(1L), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 3))))
                .willReturn(campaigns);
        given(campaignRepository.countCampaigns(eq(1L), isNull(), isNull()))
                .willReturn(5L);

        var result = campaignService.getCampaigns(1L, null, null, 2, null);

        assertThat(result.data()).hasSize(2);
        assertThat(result.paging().hasNext()).isTrue();
        assertThat(result.paging().cursors().after()).isNotNull();
        assertThat(result.paging().totalCount()).isEqualTo(5);
    }

    @Test
    void getCampaigns_invalidLimit_throwsException() {
        assertThatThrownBy(() -> campaignService.getCampaigns(1L, null, null, 0, null))
                .isInstanceOf(InvalidParameterException.class);

        assertThatThrownBy(() -> campaignService.getCampaigns(1L, null, null, 101, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void getCampaigns_invalidStatus_throwsException() {
        assertThatThrownBy(() -> campaignService.getCampaigns(1L, "INVALID", null, null, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void getCampaigns_invalidType_throwsException() {
        assertThatThrownBy(() -> campaignService.getCampaigns(1L, null, "INVALID", null, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void getCampaigns_invalidCursor_throwsException() {
        assertThatThrownBy(() -> campaignService.getCampaigns(1L, null, null, null, "not-valid-base64!!!"))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void getCampaign_found() {
        var campaign = CampaignFixture.create(10L);
        given(campaignRepository.findByIdAndAccountIdAndDeletedAtIsNull(10L, 1L))
                .willReturn(Optional.of(campaign));

        var result = campaignService.getCampaign(1L, 10L);

        assertThat(result.data().id()).isEqualTo(10L);
        assertThat(result.data().name()).isEqualTo("테스트 캠페인");
    }

    @Test
    void getCampaign_notFound_throwsException() {
        given(campaignRepository.findByIdAndAccountIdAndDeletedAtIsNull(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> campaignService.getCampaign(1L, 999L))
                .isInstanceOf(CampaignNotFoundException.class);
    }
}
