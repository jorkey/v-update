import React from 'react';
import { makeStyles } from '@material-ui/styles';
import {
  ClientVersionsInfoQuery,
  useClientVersionsInfoQuery,
} from "../../../../generated/graphql";
import GridTable from "../../../../common/components/gridTable/GridTable";
import {Version} from "../../../../common";
import {GridTableColumnParams, GridTableColumnValue} from "../../../../common/components/gridTable/GridTableColumn";
import {GridTableRowParams} from "../../../../common/components/gridTable/GridTableRow";

const useStyles = makeStyles((theme:any) => ({
  root: {},
  content: {
    padding: 0
  },
  versionsTable: {
    marginTop: 20
  },
  serviceColumn: {
    width: '150px',
    padding: '4px',
    paddingLeft: '16px'
  },
  versionColumn: {
    width: '150px',
    padding: '4px',
  },
  authorColumn: {
    width: '150px',
    padding: '4px',
  },
  commentColumn: {
    padding: '4px',
  },
  creationTime: {
    width: '250px',
    padding: '4px',
  },
  installedByColumn: {
    width: '150px',
    padding: '4px',
  },
  installTimeColumn: {
    width: '250px',
    padding: '4px',
  },
  control: {
    paddingLeft: '10px',
    textTransform: 'none'
  },
  alert: {
    marginTop: 25
  }
}));

interface LastClientVersionsTableProps {
  clientVersions:  ClientVersionsInfoQuery | undefined
}

const ClientVersionsTable: React.FC<LastClientVersionsTableProps> = (props) => {
  const { clientVersions } = props

  const classes = useStyles()

  const columns: Array<GridTableColumnParams> = [
    {
      name: 'service',
      headerName: 'Service',
      className: classes.serviceColumn,
    },
    {
      name: 'version',
      headerName: 'Version',
      className: classes.versionColumn,
    },
    {
      name: 'author',
      headerName: 'Author',
      className: classes.authorColumn,
    },
    {
      name: 'creationTime',
      headerName: 'Creation Time',
      type: 'date',
      className: classes.creationTime,
    },
    {
      name: 'comment',
      headerName: 'Comment',
      className: classes.commentColumn,
    },
    {
      name: 'installedBy',
      headerName: 'Installed By',
      className: classes.installedByColumn,
    },
    {
      name: 'installTime',
      headerName: 'Install Time',
      type: 'date',
      className: classes.installTimeColumn,
    }
  ]

  const rows = clientVersions?.clientVersionsInfo
    .sort((v1, v2) =>
      Version.compareClientDistributionVersions(v2.version, v1.version))
    .map(version => ({
      columnValues: new Map<string, GridTableColumnValue>([
        ['service', version.service],
        ['version', Version.clientDistributionVersionToString(version.version)],
        ['author', version.buildInfo.author],
        ['comment', version.buildInfo.comment?version.buildInfo.comment:''],
        ['creationTime', version.buildInfo.time],
        ['installedBy', version.installInfo.account],
        ['installTime', version.installInfo.time],
      ])} as GridTableRowParams))

  return <GridTable className={classes.versionsTable}
                    columns={columns} rows={rows?rows:[]}/>
}

export default ClientVersionsTable