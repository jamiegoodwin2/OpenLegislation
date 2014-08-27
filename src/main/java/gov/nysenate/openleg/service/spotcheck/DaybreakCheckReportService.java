package gov.nysenate.openleg.service.spotcheck;

import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.sun.org.apache.xalan.internal.utils.XMLSecurityManager;
import gov.nysenate.openleg.dao.base.LimitOffset;
import gov.nysenate.openleg.dao.base.SortOrder;
import gov.nysenate.openleg.dao.daybreak.DaybreakDao;
import gov.nysenate.openleg.dao.spotcheck.SpotCheckReportDao;
import gov.nysenate.openleg.model.base.SessionYear;
import gov.nysenate.openleg.model.bill.BaseBillId;
import gov.nysenate.openleg.model.bill.Bill;
import gov.nysenate.openleg.model.bill.BillId;
import gov.nysenate.openleg.model.daybreak.DaybreakBill;
import gov.nysenate.openleg.model.spotcheck.*;
import gov.nysenate.openleg.service.bill.BillDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static gov.nysenate.openleg.model.spotcheck.SpotCheckMismatchType.OBSERVE_DATA_MISSING;
import static gov.nysenate.openleg.model.spotcheck.SpotCheckMismatchType.REFERENCE_DATA_MISSING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * SpotCheckReportService implementation that utilizes the DaybreakCheckService to generate
 * and save reports for bill data.
 */
@Service("daybreakReport")
public class DaybreakCheckReportService implements SpotCheckReportService<BaseBillId>
{
    private static final Logger logger = LoggerFactory.getLogger(DaybreakCheckReportService.class);

    @Autowired
    private DaybreakCheckService daybreakCheckService;

    @Autowired
    private DaybreakDao daybreakDao;

    @Autowired
    private SpotCheckReportDao<BaseBillId> reportDao;

    @Autowired
    private BillDataService billDataService;

    /** --- Implemented Methods --- */

    /** {@inheritDoc} */
    @Override
    public SpotCheckReport<BaseBillId> generateReport(LocalDateTime start, LocalDateTime end) throws ReferenceDataNotFoundEx {
        // Create a new report instance
        SpotCheckReport<BaseBillId> report = new SpotCheckReport<>();
        report.setReportId(new SpotCheckReportId(SpotCheckRefType.LBDC_DAYBREAK, LocalDateTime.now()));
        // Fetch the daybreak bills that are within the given date range
        logger.info("Fetching daybreak bills...");
        Range<LocalDate> dateRange = Range.closed(start.toLocalDate(), end.toLocalDate());
        List<DaybreakBill> daybreakBills = daybreakDao.getCurrentDaybreakBills(dateRange);
        // All daybreak bills should have the same reference date.
        SpotCheckReferenceId refId = daybreakBills.get(0).getReferenceId();
        logger.info("Using Daybreak {} to generate report", refId);
        // Create a set of the base bill ids from the daybreak bills
        Set<BaseBillId> daybreakBillIds = daybreakBills.stream()
            .map(DaybreakBill::getBaseBillId).collect(Collectors.toSet());
        // And a set of all of our bill ids (excluding resolutions) present in the backing store
        Set<BaseBillId> openlegBillIds =
            billDataService.getBillIds(SessionYear.current(), LimitOffset.ALL).stream()
                .filter(id -> !id.getBillType().isResolution())
                .collect(toSet());
        // Check for base bill ids that the daybreak has but openleg does not and add 'data missing' mismatches.
        Sets.difference(daybreakBillIds, openlegBillIds).stream()
            .forEach(id -> {
                logger.info("Missing OpenLeg bill {}", id);
                SpotCheckObservation<BaseBillId> sourceMissingObs = new SpotCheckObservation<>(refId, id);
                SpotCheckMismatch mismatch = new SpotCheckMismatch(OBSERVE_DATA_MISSING, id.toString(), "");
                sourceMissingObs.addMismatch(mismatch);
                report.addObservation(sourceMissingObs);
            });
        // Perform actual spot checks for the bills common to both sets
        daybreakBills.stream()
            .filter(daybreakBill -> openlegBillIds.contains(daybreakBill.getBaseBillId()))
            .forEach(daybreakBill -> {
                Bill bill = billDataService.getBill(daybreakBill.getBaseBillId());
                report.addObservation(daybreakCheckService.check(bill, daybreakBill));
            });
        // Done with this report!
        return report;
    }

    /** {@inheritDoc} */
    @Override
    public void saveReport(SpotCheckReport<BaseBillId> report) {
        if (report == null) {
            throw new IllegalArgumentException("Supplied report cannot be null.");
        }
        reportDao.saveReport(report);
    }

    /** {@inheritDoc} */
    @Override
    public SpotCheckReport<BaseBillId> getReport(SpotCheckReportId reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("Supplied reportId cannot be null!");
        }
        try {
            return reportDao.getReport(reportId);
        }
        catch (DataAccessException ex) {
            throw new SpotCheckReportNotFoundEx(reportId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<SpotCheckReportId> getReportIds(LocalDateTime start, LocalDateTime end,
                                                SortOrder dateOrder, LimitOffset limOff) {
        if (limOff == null) { limOff = LimitOffset.ALL; }
        if (dateOrder == null) { dateOrder = SortOrder.ASC; }
        return reportDao.getReportIds(SpotCheckRefType.LBDC_DAYBREAK, start, end, dateOrder, limOff);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteReport(SpotCheckReportId reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("Supplied reportId to delete cannot be null");
        }
        reportDao.deleteReport(reportId);
    }
}