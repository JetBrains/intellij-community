// The one in-page breadcrumb. DocPage shows it in the crumb row at the top of the document
// (PageCrumbRow of pageLayout.tsx); a canvas page, such as a flow, mounts it in its prose island.
// Trails come from crumbsForRoute.

import { Fragment } from 'react'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from './ui/breadcrumb'
import { cn } from '../lib/utils'
import type { RouteCrumb } from '../crumbs'

export function PageBreadcrumb({ crumbs, className }: { crumbs: RouteCrumb[], className?: string }) {
  if (crumbs.length === 0) {
    return null
  }
  return (
    <Breadcrumb className={cn('page-breadcrumb min-w-0', className)}>
      <BreadcrumbList className="flex-nowrap">
        {crumbs.map((crumb, index) => {
          const leaf = index === crumbs.length - 1
          return (
            <Fragment key={index}>
              {index > 0 && <BreadcrumbSeparator />}
              <BreadcrumbItem className={cn(leaf && 'min-w-0')}>
                {leaf
                  ? (
                      <BreadcrumbPage className={cn('truncate', crumb.mono && 'font-mono text-xs')}>
                        {crumb.label}
                      </BreadcrumbPage>
                    )
                  : crumb.href
                    ? <BreadcrumbLink render={<a href={crumb.href}>{crumb.label}</a>} />
                    : <span>{crumb.label}</span>}
              </BreadcrumbItem>
            </Fragment>
          )
        })}
      </BreadcrumbList>
    </Breadcrumb>
  )
}
