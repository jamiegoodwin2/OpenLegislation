package gov.nysenate.openleg.service.hearing.data;

import gov.nysenate.openleg.dao.base.LimitOffset;
import gov.nysenate.openleg.dao.base.SortOrder;
import gov.nysenate.openleg.model.base.SessionYear;
import gov.nysenate.openleg.model.hearing.PublicHearing;
import gov.nysenate.openleg.model.hearing.PublicHearingFile;
import gov.nysenate.openleg.model.hearing.PublicHearingId;

import java.util.List;

public interface PublicHearingDataService
{
    /**
     * Retrieves a {@link PublicHearing} instance from a {@link PublicHearingId}.
     * @param publicHearingId
     * @return
     */
    public PublicHearing getPublicHearing(PublicHearingId publicHearingId);

    /**
     * Retrieves a List of {@link PublicHearingId} for a given year.
     * @param year The year to retrieve PublicHearingId's for.
     * @param limitOffset Restrict the number of results.
     * @return
     */
    public List<PublicHearingId> getPublicHearingIds(int year, SortOrder dateOrder, LimitOffset limitOffset);

    /**
     * Saves a {@link PublicHearing} to the backing store.
     * The PublicHearing is inserted if it is a new instance, Updated otherwise.
     * @param publicHearing
     * @param publicHearingFile
     * @param postUpdateEvent
     */
    public void savePublicHearing(PublicHearing publicHearing, PublicHearingFile publicHearingFile, boolean postUpdateEvent);

}
